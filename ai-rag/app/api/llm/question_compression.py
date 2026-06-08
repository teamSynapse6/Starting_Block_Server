import json
import re
from typing import Any

from app.api.llm.client import create_llm_client


CHECK_DUPLICATE_PROMPT = """
당신은 창업 지원사업에 대한 질문을 기존 질문들과 비교합니다.
입력 JSON의 oldQuestions는 기존 질문 목록이고, newQuestion은 새 질문입니다.
기존 질문 중 의미가 매우 유사한 질문이 있으면 그 질문의 id를 반환하고, 없으면 0을 반환하세요.
반드시 {"questionId": 숫자} 형태의 JSON만 출력하세요.
""".strip()


GROUP_QUESTIONS_PROMPT = """
당신은 창업 지원사업 담당자에게 전달할 질문 목록을 유사한 질문끼리 묶어 정리합니다.
입력 JSON의 questions 배열에는 questionId와 content가 있습니다.
서로 의미가 유사한 질문은 하나로 묶고, content에는 담당자가 답변하기 좋은 대표 질문을 작성하세요.
반드시 [{"questionId":[숫자], "content":"질문"}] 형태의 JSON 배열만 출력하세요.
""".strip()


def _extract_json(value: str) -> Any:
    text = (value or "").strip()
    if not text:
        raise ValueError("empty-llm-response")

    fenced = re.search(r"```(?:json)?\s*(.*?)\s*```", text, re.DOTALL)
    if fenced:
        text = fenced.group(1).strip()

    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass

    start_candidates = [index for index in [text.find("{"), text.find("[")] if index >= 0]
    if not start_candidates:
        raise ValueError("json-start-not-found")
    start = min(start_candidates)
    end = max(text.rfind("}"), text.rfind("]"))
    if end < start:
        raise ValueError("json-boundary-not-found")
    return json.loads(text[start:end + 1])


def _question_id(item: dict[str, Any]) -> int | None:
    raw = item.get("questionId", item.get("qid"))
    try:
        return int(raw)
    except (TypeError, ValueError):
        return None


def _normalize_duplicate_response(value: Any, old_questions: list[dict[str, Any]]) -> dict[str, int]:
    allowed_ids = {_question_id(item) for item in old_questions}
    allowed_ids.discard(None)

    raw = value.get("questionId") if isinstance(value, dict) else value
    try:
        question_id = int(raw)
    except (TypeError, ValueError):
        question_id = 0

    if question_id not in allowed_ids:
        question_id = 0
    return {"questionId": question_id}


def _fallback_groups(questions: list[dict[str, Any]]) -> list[dict[str, Any]]:
    groups: list[dict[str, Any]] = []
    for item in questions:
        question_id = _question_id(item)
        content = str(item.get("content") or "").strip()
        if question_id is None or not content:
            continue
        groups.append({"questionId": [question_id], "content": content})
    return groups


def _normalize_group_response(value: Any, questions: list[dict[str, Any]]) -> list[dict[str, Any]]:
    allowed_ids = {_question_id(item) for item in questions}
    allowed_ids.discard(None)
    normalized: list[dict[str, Any]] = []
    used_ids: set[int] = set()

    if not isinstance(value, list):
        return _fallback_groups(questions)

    for group in value:
        if not isinstance(group, dict):
            continue
        raw_ids = group.get("questionId", [])
        if isinstance(raw_ids, (int, str)):
            raw_ids = [raw_ids]
        ids: list[int] = []
        for raw_id in raw_ids:
            try:
                question_id = int(raw_id)
            except (TypeError, ValueError):
                continue
            if question_id in allowed_ids and question_id not in used_ids:
                ids.append(question_id)
                used_ids.add(question_id)
        content = str(group.get("content") or "").strip()
        if ids and content:
            normalized.append({"questionId": ids, "content": content})

    if used_ids != allowed_ids:
        by_id = {_question_id(item): str(item.get("content") or "").strip() for item in questions}
        for question_id in sorted(allowed_ids - used_ids):
            content = by_id.get(question_id, "")
            if content:
                normalized.append({"questionId": [question_id], "content": content})

    return normalized


async def check_duplicate(payload: dict[str, Any]) -> dict[str, int]:
    old_questions = payload.get("oldQuestions") or []
    if not old_questions:
        return {"questionId": 0}

    messages = [
        {"role": "system", "content": CHECK_DUPLICATE_PROMPT},
        {"role": "user", "content": json.dumps(payload, ensure_ascii=False)},
    ]
    client = create_llm_client()
    response = await client.chat(messages)
    return _normalize_duplicate_response(_extract_json(response), old_questions)


async def group_questions(payload: dict[str, Any]) -> list[dict[str, Any]]:
    questions = payload.get("questions") or []
    if not questions:
        return []

    messages = [
        {"role": "system", "content": GROUP_QUESTIONS_PROMPT},
        {"role": "user", "content": json.dumps(payload, ensure_ascii=False)},
    ]
    client = create_llm_client()
    response = await client.chat(messages)
    return _normalize_group_response(_extract_json(response), questions)
