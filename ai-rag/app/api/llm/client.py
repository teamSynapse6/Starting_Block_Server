import asyncio
import os
import subprocess
import time
from typing import AsyncIterator, Protocol

from ollama import AsyncClient
from ollama._types import ResponseError

from app.api.llm.prompts import instructions
from app.core.config import (
    OLLAMA_AUTO_START,
    OLLAMA_BASE_URL,
    OLLAMA_FLASH_ATTENTION,
    OLLAMA_KEEP_ALIVE,
    OLLAMA_KV_CACHE_TYPE,
    OLLAMA_MAX_QUEUE,
    OLLAMA_MODEL,
    OLLAMA_NUM_CTX,
    OLLAMA_NUM_PARALLEL,
    OLLAMA_NUM_PREDICT,
    OLLAMA_REPEAT_PENALTY,
    OLLAMA_SCHED_SPREAD,
    OLLAMA_THINK,
    OLLAMA_TOP_K,
    OLLAMA_TOP_P,
)


class LlmClient(Protocol):
    async def ensure_model_loaded(self):
        ...

    async def rag_chat_stream(
        self,
        question: str,
        context: str,
        history: list[dict] | None = None,
        summary_text: str | None = None,
    ) -> AsyncIterator[dict]:
        ...


def build_rag_messages(
    context: str,
    question: str,
    history: list[dict] | None,
    summary_text: str | None,
) -> list[dict]:
    context_text = context.strip() if context and context.strip() else "검색된 관련 발췌문이 없습니다."
    prompt_system = (
        f"{instructions.strip()}\n\n"
        f"[검색된 관련 공고 발췌문]\n{context_text}\n"
    )
    messages: list[dict] = [{"role": "system", "content": prompt_system}]
    if summary_text and summary_text.strip():
        messages.append({"role": "system", "content": f"[이전 대화 요약]\n{summary_text.strip()}"})
    if history:
        for item in history:
            role = item.get("role")
            content = (item.get("content") or "").strip()
            if not content or role not in ("user", "assistant"):
                continue
            messages.append({"role": role, "content": content})
    messages.append({"role": "user", "content": question})
    return messages


def build_rag_prompt(
    context: str,
    question: str,
    history: list[dict] | None,
    summary_text: str | None,
) -> str:
    messages = build_rag_messages(context, question, history, summary_text)
    sections: list[str] = []
    for message in messages:
        role = message.get("role", "user")
        content = (message.get("content") or "").strip()
        if not content:
            continue
        if role == "system":
            sections.append(f"[시스템 지침]\n{content}")
        elif role == "assistant":
            sections.append(f"[이전 답변]\n{content}")
        else:
            sections.append(f"[사용자 질문]\n{content}")
    sections.append("[답변]\n")
    return "\n\n".join(sections)


class OllamaLlmClient:
    def __init__(self):
        self.base_url = OLLAMA_BASE_URL.rstrip("/")
        self.client = AsyncClient(host=self.base_url)
        self.model_loaded = False
        self.last_request_at = 0.0
        self._lock = asyncio.Lock()

    async def _generate(self, **kwargs):
        if OLLAMA_THINK is not None:
            kwargs["think"] = OLLAMA_THINK
        try:
            return await self.client.generate(**kwargs)
        except TypeError as error:
            if "think" not in kwargs or "think" not in str(error):
                raise
            kwargs.pop("think", None)
            return await self.client.generate(**kwargs)
        except ResponseError as error:
            if "think" not in kwargs or "think" not in str(error).lower():
                raise
            kwargs.pop("think", None)
            return await self.client.generate(**kwargs)

    async def chat(self, messages: list[dict]) -> str:
        await self.ensure_model_loaded()
        prompt = "\n\n".join(
            f"[{message.get('role', 'user')}]\n{(message.get('content') or '').strip()}"
            for message in messages
            if (message.get("content") or "").strip()
        )

        response = await self._generate(
            model=OLLAMA_MODEL,
            prompt=prompt,
            stream=False,
            keep_alive=OLLAMA_KEEP_ALIVE,
            options={
                "num_predict": OLLAMA_NUM_PREDICT,
                "num_ctx": OLLAMA_NUM_CTX,
                "top_p": OLLAMA_TOP_P,
                "top_k": OLLAMA_TOP_K,
                "repeat_penalty": OLLAMA_REPEAT_PENALTY,
            },
        )

        self.last_request_at = time.monotonic()
        self.model_loaded = True

        content = getattr(response, "response", "") or ""
        if not content:
            raise ValueError("OLLAMA_EMPTY_RESPONSE")
        return content

    def _timings_from_response(self, resp) -> dict[str, int]:
        result: dict[str, int] = {}
        for key in ["total_duration", "load_duration", "prompt_eval_duration", "eval_duration"]:
            value = getattr(resp, key, None)
            if isinstance(value, (int, float)):
                result[f"{key}_ms"] = int(value / 1_000_000)
        for key in ["prompt_eval_count", "eval_count"]:
            value = getattr(resp, key, None)
            if isinstance(value, int):
                result[key] = value
        return result

    async def rag_chat(
        self,
        question: str,
        context: str,
        history: list[dict] | None = None,
        summary_text: str | None = None,
    ) -> tuple[str, dict[str, int]]:
        await self.ensure_model_loaded()

        prompt = build_rag_prompt(context, question, history, summary_text)

        response = await self._generate(
            model=OLLAMA_MODEL,
            prompt=prompt,
            stream=False,
            keep_alive=OLLAMA_KEEP_ALIVE,
            options={
                "num_predict": OLLAMA_NUM_PREDICT,
                "num_ctx": OLLAMA_NUM_CTX,
                "top_p": OLLAMA_TOP_P,
                "top_k": OLLAMA_TOP_K,
                "repeat_penalty": OLLAMA_REPEAT_PENALTY,
            },
        )

        self.last_request_at = time.monotonic()
        self.model_loaded = True

        content = getattr(response, "response", "") or ""
        if not content.strip():
            raise ValueError("OLLAMA_EMPTY_RESPONSE")

        internal_timings = self._timings_from_response(response)
        return content.strip(), internal_timings

    async def rag_chat_stream(
        self,
        question: str,
        context: str,
        history: list[dict] | None = None,
        summary_text: str | None = None,
    ) -> AsyncIterator[dict]:
        await self.ensure_model_loaded()

        prompt = build_rag_prompt(context, question, history, summary_text)

        full_parts: list[str] = []
        thinking_parts: list[str] = []
        internal_timings: dict[str, int] = {}
        last_chunk = None

        async for chunk in await self._generate(
            model=OLLAMA_MODEL,
            prompt=prompt,
            stream=True,
            keep_alive=OLLAMA_KEEP_ALIVE,
            options={
                "num_predict": OLLAMA_NUM_PREDICT,
                "num_ctx": OLLAMA_NUM_CTX,
                "top_p": OLLAMA_TOP_P,
                "top_k": OLLAMA_TOP_K,
                "repeat_penalty": OLLAMA_REPEAT_PENALTY,
            },
        ):
            thinking_token = getattr(chunk, "thinking", "") or ""
            if thinking_token:
                thinking_parts.append(thinking_token)
                yield {"type": "thinking", "content": thinking_token}

            token = getattr(chunk, "response", "") or ""
            if token:
                full_parts.append(token)
                yield {"type": "token", "content": token}
            if chunk.done:
                last_chunk = chunk

        self.last_request_at = time.monotonic()
        self.model_loaded = True

        if last_chunk is not None:
            internal_timings = self._timings_from_response(last_chunk)

        full_text = "".join(full_parts).strip()
        if not full_text:
            raise ValueError("OLLAMA_EMPTY_RESPONSE")

        yield {
            "type": "final",
            "content": full_text,
            "thinking": "".join(thinking_parts).strip(),
            "internal_timings": internal_timings,
            "raw_metadata": {},
        }

    async def health(self) -> bool:
        try:
            await self.client.list()
            return True
        except Exception:
            return False

    async def is_model_loaded(self) -> bool:
        try:
            response = await self.client.ps()
            models = getattr(response, "models", None) or []
            for item in models:
                name = getattr(item, "model", "") or getattr(item, "name", "")
                if name == OLLAMA_MODEL or name.startswith(f"{OLLAMA_MODEL}:"):
                    return True
            return False
        except Exception:
            return False

    async def ensure_server_running(self):
        if await self.health():
            return

        if not OLLAMA_AUTO_START:
            raise RuntimeError(
                "Ollama 서버에 연결할 수 없습니다. "
                f"OLLAMA_BASE_URL={self.base_url} 값을 확인하고, 호스트 Ollama가 외부 접속을 허용하도록 OLLAMA_HOST=0.0.0.0:11434로 실행되어야 합니다."
            )

        try:
            env = os.environ.copy()
            env["OLLAMA_FLASH_ATTENTION"] = "1" if OLLAMA_FLASH_ATTENTION else "0"
            env["OLLAMA_SCHED_SPREAD"] = "1" if OLLAMA_SCHED_SPREAD else "0"
            env["OLLAMA_NUM_PARALLEL"] = str(OLLAMA_NUM_PARALLEL)
            env["OLLAMA_MAX_QUEUE"] = str(OLLAMA_MAX_QUEUE)
            if OLLAMA_KV_CACHE_TYPE:
                env["OLLAMA_KV_CACHE_TYPE"] = OLLAMA_KV_CACHE_TYPE

            subprocess.Popen(
                ["ollama", "serve"],
                env=env,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
        except FileNotFoundError as error:
            raise RuntimeError("Ollama 실행 파일을 찾을 수 없습니다.") from error

        for _ in range(30):
            if await self.health():
                return
            await asyncio.sleep(0.5)

        raise RuntimeError("Ollama 서버가 시작되지 않았습니다.")

    async def ensure_model_loaded(self):
        async with self._lock:
            await self.ensure_server_running()

            if self.model_loaded or await self.is_model_loaded():
                self.model_loaded = True
                self.last_request_at = time.monotonic()
                return

            await self.client.generate(
                model=OLLAMA_MODEL,
                prompt=" ",
                stream=False,
                keep_alive=OLLAMA_KEEP_ALIVE,
                options={
                    "num_ctx": OLLAMA_NUM_CTX,
                    "num_predict": 1,
                },
            )

            self.model_loaded = True
            self.last_request_at = time.monotonic()

    async def unload_model(self):
        async with self._lock:
            if not self.model_loaded:
                return

            await self.client.generate(
                model=OLLAMA_MODEL,
                prompt="",
                stream=False,
                keep_alive=0,
            )

            self.model_loaded = False

    async def unload_if_idle(self, idle_seconds: int):
        if not self.model_loaded:
            return

        if self.last_request_at <= 0:
            return

        idle_for = time.monotonic() - self.last_request_at
        if idle_for >= idle_seconds:
            await self.unload_model()


_OLLAMA_CLIENT: OllamaLlmClient | None = None

def create_llm_client() -> LlmClient:
    global _OLLAMA_CLIENT
    if _OLLAMA_CLIENT is None:
        _OLLAMA_CLIENT = OllamaLlmClient()
    return _OLLAMA_CLIENT


OllamaClient = OllamaLlmClient
