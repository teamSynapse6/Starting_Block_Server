import argparse
import asyncio
import json
import os
import re
import subprocess
import sys
import tempfile
import time
import traceback
from typing import Any


def _json_stdout(payload: dict[str, Any]) -> int:
    print(json.dumps(payload, ensure_ascii=False))
    return 0


def _json_error(message: str) -> int:
    print(json.dumps({"status": "failed", "error": message}, ensure_ascii=False), file=sys.stderr)
    return 1


def validation(_args: argparse.Namespace) -> int:
    from app.core.storage import MinioStorage

    storage = MinioStorage()
    storage.ensure_bucket()
    return _json_stdout({"file_ids": storage.list_processed_ids()})


def get_announcement(args: argparse.Namespace) -> int:
    from app.core.storage import MinioStorage

    if not args.id:
        return _json_error("file_id가 없습니다")

    storage = MinioStorage()
    storage.ensure_bucket()
    content = storage.get_processed_text(args.id)
    if content is None:
        return _json_error("파일이 없습니다")

    sys.stdout.write(content)
    sys.stdout.flush()
    return 0


def delete_announcement(_args: argparse.Namespace) -> int:
    from app.api.announcement.vector_indexer import AnnouncementVectorIndexer
    from app.core.config import EMBEDDING_DEVICE
    from app.core.storage import MinioStorage

    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError as error:
        return _json_error(f"invalid-json: {error}")

    file_ids = payload.get("id") or []
    if not file_ids:
        return _json_stdout({"error": "No file ids provided"})

    storage = MinioStorage()
    storage.ensure_bucket()
    indexer = AnnouncementVectorIndexer(EMBEDDING_DEVICE)
    indexer.ensure_ready()

    deleted_items: list[int] = []
    failed_items: list[int] = []
    vector_deleted_items: list[int] = []
    vector_delete_failed_items: list[int] = []

    for file_id_raw in file_ids:
        file_id = int(file_id_raw)
        try:
            storage.delete_announcement(file_id)
            deleted_items.append(file_id)
        except Exception:
            failed_items.append(file_id)
            continue

        try:
            indexer.delete_announcement(file_id)
            vector_deleted_items.append(file_id)
        except Exception:
            vector_delete_failed_items.append(file_id)

    return _json_stdout(
        {
            "status": "finished",
            "deleted_items": deleted_items,
            "failed_items": failed_items,
            "vector_delete_queued_items": [],
            "vector_delete_enqueue_failed_items": vector_delete_failed_items,
            "vector_deleted_items": vector_deleted_items,
        }
    )


def upload(_args: argparse.Namespace) -> int:
    import httpx

    from app.api.announcement.file_pipeline import (
        convert_hwp_path_to_text,
        convert_pdf_bytes_to_text,
        detect_file_format,
    )
    from app.api.announcement.vector_indexer import AnnouncementVectorIndexer
    from app.core.config import EMBEDDING_DEVICE
    from app.core.db_models import ensure_database_and_tables
    from app.core.storage import MinioStorage

    try:
        items = json.load(sys.stdin)
    except json.JSONDecodeError as error:
        return _json_error(f"invalid-json: {error}")

    ensure_database_and_tables()
    storage = MinioStorage()
    storage.ensure_bucket()
    indexer = AnnouncementVectorIndexer(EMBEDDING_DEVICE)
    indexer.ensure_ready()

    success_items: list[int] = []
    failed_items: list[int] = []
    indexing_failed_items: list[int] = []

    with httpx.Client(timeout=30.0) as client:
        for item in items:
            file_id = int(item["id"])
            file_format = str(item["format"]).lower()
            if file_format not in {"hwp", "pdf", "txt"}:
                failed_items.append(file_id)
                continue

            temp_file_path = None
            try:
                response = client.get(item["url"])
                response.raise_for_status()
                file_bytes = response.content

                actual_format = detect_file_format(file_bytes)
                if actual_format != file_format:
                    raise ValueError(f"expected {file_format}, got {actual_format}")

                storage.put_raw_bytes(file_id, file_format, file_bytes)

                if actual_format == "pdf":
                    text = convert_pdf_bytes_to_text(file_bytes)
                elif actual_format == "hwp":
                    with tempfile.NamedTemporaryFile(delete=False, suffix=".hwp") as temp_file:
                        temp_file.write(file_bytes)
                        temp_file_path = temp_file.name
                    text = convert_hwp_path_to_text(temp_file_path)
                else:
                    text = file_bytes.decode("utf-8", errors="replace")

                storage.put_processed_text(file_id, text)
                success_items.append(file_id)

                try:
                    indexer.upsert_announcement(file_id, text)
                except Exception:
                    indexing_failed_items.append(file_id)
            except Exception:
                failed_items.append(file_id)
            finally:
                if temp_file_path and os.path.exists(temp_file_path):
                    os.remove(temp_file_path)

    return _json_stdout(
        {
            "status": "finished",
            "success_items": success_items,
            "failed_items": failed_items,
            "indexing_queued_items": [],
            "indexing_failed_items": indexing_failed_items,
        }
    )


def _clip_text(value: str, max_chars: int) -> str:
    if max_chars <= 0:
        return ""
    if len(value) <= max_chars:
        return value
    return value[-max_chars:]


def _build_history_summary(messages: list[dict], max_chars: int) -> str:
    lines: list[str] = []
    for item in messages:
        role = item.get("role")
        content = (item.get("content") or "").replace("\n", " ").strip()
        if not content:
            continue
        tag = "U" if role == "user" else "A"
        lines.append(f"{tag}: {content}")
    return _clip_text("\n".join(lines), max_chars)


def _merge_summary(existing: str, latest: str, max_chars: int) -> str:
    merged = "\n".join(part for part in [existing.strip(), latest.strip()] if part and part.strip())
    return _clip_text(merged, max_chars)


def _safe_serialize(obj: Any) -> Any:
    if obj is None:
        return None
    if isinstance(obj, (str, int, float, bool)):
        return obj
    if isinstance(obj, dict):
        return {k: _safe_serialize(v) for k, v in obj.items()}
    if isinstance(obj, (list, tuple)):
        return [_safe_serialize(item) for item in obj]
    try:
        return str(obj)
    except Exception:
        return None


def _split_fallback_units(text: str, max_unit_chars: int = 900) -> list[str]:
    units: list[str] = []
    for block in re.split(r"\n\s*\n+", text):
        block = re.sub(r"[ \t]+", " ", block).strip()
        if not block:
            continue
        if len(block) <= max_unit_chars:
            units.append(block)
            continue
        sentences = re.split(r"(?<=[.!?。！？])\s+|\n+", block)
        current = ""
        for sentence in sentences:
            sentence = sentence.strip()
            if not sentence:
                continue
            if current and len(current) + len(sentence) + 1 > max_unit_chars:
                units.append(current.strip())
                current = sentence
            else:
                current = f"{current} {sentence}".strip()
        if current:
            units.append(current.strip())
    return units


def _query_terms(query: str) -> set[str]:
    terms = set()
    for token in re.findall(r"[0-9A-Za-z가-힣]+", query.lower()):
        if len(token) >= 2:
            terms.add(token)
    return terms


def _build_keyword_fallback_context(text: str, query: str, max_chars: int, top_k: int) -> tuple[str, int]:
    terms = _query_terms(query)
    if not terms:
        return "", 0

    scored: list[tuple[int, int, str]] = []
    for index, unit in enumerate(_split_fallback_units(text)):
        unit_lower = unit.lower()
        score = sum(1 for term in terms if term in unit_lower)
        if score > 0:
            scored.append((score, -index, unit))

    if not scored:
        return "", 0

    selected = [unit for _, _, unit in sorted(scored, reverse=True)[:max(1, top_k)]]
    context_parts: list[str] = []
    current_len = 0
    for idx, unit in enumerate(selected, start=1):
        part = f"[fallback:{idx}]\n{unit}"
        next_len = current_len + len(part) + (2 if context_parts else 0)
        if context_parts and next_len > max_chars:
            break
        if not context_parts and len(part) > max_chars:
            part = part[:max_chars]
        context_parts.append(part)
        current_len += len(part) + 2

    return "\n\n".join(context_parts), len(context_parts)


def _sse(event: str, payload: dict[str, Any]) -> str:
    return f"event: {event}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"


def _release_cuda_cache():
    try:
        import gc
        gc.collect()
        import torch
        if torch.cuda.is_available():
            torch.cuda.empty_cache()
            torch.cuda.ipc_collect()
    except Exception:
        pass


def _gpu_stats() -> dict[str, int] | None:
    try:
        result = subprocess.run(
            [
                "nvidia-smi",
                "--query-gpu=utilization.gpu,memory.free,memory.total",
                "--format=csv,noheader,nounits",
            ],
            capture_output=True,
            text=True,
            timeout=2,
            check=True,
        )
        rows = []
        for line in result.stdout.splitlines():
            util, free_mb, total_mb = [int(part.strip()) for part in line.split(",")]
            rows.append({"utilization": util, "free_memory_mb": free_mb, "total_memory_mb": total_mb})
        if not rows:
            return None
        return {
            "utilization": max(row["utilization"] for row in rows),
            "free_memory_mb": sum(row["free_memory_mb"] for row in rows),
            "total_memory_mb": sum(row["total_memory_mb"] for row in rows),
        }
    except Exception:
        return None


def llm_start(args: argparse.Namespace) -> int:
    from app.api.llm.session_store import MySQLSessionStore
    from app.core.db_models import ensure_database_and_tables

    try:
        store = MySQLSessionStore()
        user_id = args.user_id
        try:
            thread_id = store.create_session(user_id)
        except Exception:
            ensure_database_and_tables()
            thread_id = store.create_session(user_id)
        return _json_stdout({"thread_id": thread_id})
    except Exception as error:
        traceback.print_exc(file=sys.stderr)
        return _json_error(f"세션을 생성하는 동안 오류가 발생했습니다: {error}")


def llm_delete(args: argparse.Namespace) -> int:
    from app.api.llm.archive_store import MySQLArchiveStore
    from app.api.llm.generation_store import RedisGenerationStore
    from app.api.llm.session_store import MySQLSessionStore

    thread_id = args.thread_id
    if not thread_id:
        return _json_error("thread_id 파라미터가 필요합니다.")

    store = MySQLSessionStore()
    archive_store = MySQLArchiveStore()

    session = store.get_session_any(thread_id)
    if session is None:
        return _json_stdout({"id": thread_id, "deleted": False})

    try:
        archive_store.archive_session(session)
        RedisGenerationStore().delete(thread_id)
        return _json_stdout({"id": thread_id, "deleted": True})
    except Exception as error:
        return _json_error(f"세션 삭제 중 오류가 발생했습니다: {error}")


def llm_cancel(args: argparse.Namespace) -> int:
    from app.api.llm.generation_store import RedisGenerationStore
    from app.api.llm.session_store import MySQLSessionStore

    thread_id = args.thread_id
    if not thread_id:
        return _json_error("thread_id 파라미터가 필요합니다.")

    reason = args.reason or "user_cancelled"
    try:
        generation = RedisGenerationStore().mark_cancelled(thread_id, reason)
        db_cancelled = MySQLSessionStore().cancel_session(thread_id)
        return _json_stdout(
            {
                "thread_id": thread_id,
                "cancelled": True,
                "db_cancelled": db_cancelled,
                "generation": generation,
            }
        )
    except Exception as error:
        traceback.print_exc(file=sys.stderr)
        return _json_error(f"취소 처리 중 오류가 발생했습니다: {error}")


def llm_mark_queued(_args: argparse.Namespace) -> int:
    from app.api.llm.generation_store import RedisGenerationStore

    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError as error:
        return _json_error(f"invalid-json: {error}")

    thread_id = payload.get("thread_id")
    message = payload.get("message")
    announcement_id = payload.get("announcement_id")
    if not thread_id or not message or announcement_id is None:
        return _json_error("thread_id, message, announcement_id가 필요합니다")

    generation_store = RedisGenerationStore()
    generation_store.start(thread_id, int(announcement_id), message)
    generation_store.update(thread_id, status="queued", stage="dynamic_queue_waiting")
    return _json_stdout({"thread_id": thread_id, "status": "queued", "stage": "dynamic_queue_waiting"})


def llm_status(args: argparse.Namespace) -> int:
    from app.api.llm.generation_store import RedisGenerationStore
    from app.api.llm.session_store import MySQLSessionStore
    from app.core.db_models import ensure_database_and_tables

    thread_id = args.thread_id
    if not thread_id:
        return _json_error("thread_id 파라미터가 필요합니다.")

    try:
        store = MySQLSessionStore()
        generation_store = RedisGenerationStore()
        try:
            session = store.get_session_any(thread_id)
        except Exception:
            ensure_database_and_tables()
            session = store.get_session(thread_id)
        generation = generation_store.get(thread_id)

        return _json_stdout(
            {
                "thread_id": thread_id,
                "session": session,
                    "generation": generation or {
                        "thread_id": thread_id,
                        "status": "idle",
                        "stage": "idle",
                        "thinking_response": "",
                        "partial_response": "",
                        "error_message": "",
                        "cancel_requested": False,
                        "cancel_reason": "",
                    },
            }
        )
    except Exception as error:
        traceback.print_exc(file=sys.stderr)
        return _json_error(f"세션 상태 조회 중 오류가 발생했습니다: {error}")


def llm_history(args: argparse.Namespace) -> int:
    from app.api.llm.session_store import MySQLSessionStore
    from app.core.db_models import ensure_database_and_tables

    thread_id = args.thread_id
    if not thread_id:
        return _json_error("thread_id 파라미터가 필요합니다.")

    try:
        store = MySQLSessionStore()
        try:
            session = store.get_session_any(thread_id)
        except Exception:
            ensure_database_and_tables()
            session = store.get_session_any(thread_id)
        return _json_stdout({"thread_id": thread_id, "session": session})
    except Exception as error:
        traceback.print_exc(file=sys.stderr)
        return _json_error(f"대화 기록 조회 중 오류가 발생했습니다: {error}")


def llm_events(args: argparse.Namespace) -> int:
    from app.api.llm.generation_store import RedisGenerationStore

    thread_id = args.thread_id
    if not thread_id:
        return _json_error("thread_id 파라미터가 필요합니다.")

    try:
        store = RedisGenerationStore()
        after_seq = int(args.after_seq or 0)
        generation = store.get(thread_id)
        return _json_stdout(
            {
                "thread_id": thread_id,
                "after_seq": after_seq,
                "events": store.events_after(thread_id, after_seq),
                "generation": generation,
            }
        )
    except Exception as error:
        traceback.print_exc(file=sys.stderr)
        return _json_error(f"SSE 이벤트 조회 중 오류가 발생했습니다: {error}")


async def _llm_chat_async() -> int:
    from ollama import RequestError, ResponseError

    from app.api.announcement.vector_indexer import AnnouncementVectorIndexer
    from app.api.llm.client import create_llm_client
    from app.api.llm.generation_store import RedisGenerationStore
    from app.api.llm.session_store import MySQLSessionStore
    from app.core.config import (
        EMBEDDING_DEVICE,
        LLM_GPU_MAX_UTILIZATION,
        LLM_GPU_MIN_FREE_MEMORY_MB,
        LLM_GPU_WAIT_ENABLED,
        LLM_GPU_WAIT_INTERVAL_SECONDS,
        LLM_GPU_WAIT_TIMEOUT_SECONDS,
        LLM_HISTORY_MAX_TURNS,
        LLM_SUMMARY_MAX_CHARS,
        LLM_SUMMARY_RECENT_MESSAGES,
        LLM_SUMMARY_TRIGGER_MESSAGES,
        RAG_CONTEXT_MAX_CHARS,
        RAG_TOP_K,
    )
    from app.core.storage import MinioStorage

    try:
        payload = json.load(sys.stdin)
    except json.JSONDecodeError as error:
        sys.stdout.write(_sse("error", {"detail": f"invalid-json: {error}"}))
        sys.stdout.flush()
        return 1

    thread_id = payload.get("thread_id")
    message = payload.get("message")
    announcement_id = payload.get("announcement_id")
    if not thread_id or not message or announcement_id is None:
        sys.stdout.write(_sse("error", {"detail": "thread_id, message, announcement_id가 필요합니다"}))
        sys.stdout.flush()
        return 1

    store = MySQLSessionStore()
    generation_store = RedisGenerationStore()
    storage = MinioStorage()
    storage.ensure_bucket()
    indexer = AnnouncementVectorIndexer(EMBEDDING_DEVICE)
    indexer.ensure_ready()
    llm_client = create_llm_client()

    started_at = time.perf_counter()
    checkpoints: list[tuple[str, float]] = [("request_received", started_at)]

    last_partial_save_at = 0.0
    last_thinking_save_at = 0.0

    def emit(event: str, payload_item: dict[str, Any]):
        try:
            generation_store.append_event(thread_id, event, payload_item)
        except Exception:
            traceback.print_exc(file=sys.stderr)
        sys.stdout.write(_sse(event, payload_item))
        sys.stdout.flush()

    def mark(name: str):
        checkpoints.append((name, time.perf_counter()))

    async def cancel_if_requested(stage: str) -> bool:
        if await asyncio.to_thread(generation_store.is_cancel_requested, thread_id):
            await asyncio.to_thread(generation_store.mark_cancelled, thread_id)
            await asyncio.to_thread(store.cancel_session, thread_id)
            emit("status", {"stage": "cancelled", "from_stage": stage})
            return True
        return False

    def build_time_spend(ollama_internal: dict | None = None) -> dict:
        durations_ms: dict[str, int] = {}
        for index in range(1, len(checkpoints)):
            prev_name, prev_ts = checkpoints[index - 1]
            curr_name, curr_ts = checkpoints[index]
            durations_ms[f"{prev_name}->{curr_name}"] = int((curr_ts - prev_ts) * 1000)
        durations_ms["total_ms"] = int((checkpoints[-1][1] - started_at) * 1000)
        if ollama_internal:
            durations_ms["ollama_internal"] = ollama_internal
        return durations_ms

    async def wait_for_gpu_capacity():
        if not LLM_GPU_WAIT_ENABLED:
            return

        waited = 0.0
        while waited < LLM_GPU_WAIT_TIMEOUT_SECONDS:
            stats = _gpu_stats()
            if stats is None:
                return
            busy = (
                stats["utilization"] >= LLM_GPU_MAX_UTILIZATION
                or stats["free_memory_mb"] < LLM_GPU_MIN_FREE_MEMORY_MB
            )
            if not busy:
                return
            emit(
                "status",
                {
                    "stage": "gpu_waiting",
                    "waited_seconds": int(waited),
                    **stats,
                },
            )
            await asyncio.to_thread(
                generation_store.update,
                thread_id,
                status="queued",
                stage="gpu_waiting",
            )
            await asyncio.sleep(LLM_GPU_WAIT_INTERVAL_SECONDS)
            waited += LLM_GPU_WAIT_INTERVAL_SECONDS

    try:
        announcement_id = int(announcement_id)
        await asyncio.to_thread(generation_store.start, thread_id, announcement_id, message)
        emit("status", {"stage": "request_received"})
        await asyncio.to_thread(generation_store.update, thread_id, status="queued", stage="request_received")
        if await cancel_if_requested("request_received"):
            return 0

        session_task = asyncio.to_thread(store.get_session, thread_id)
        llm_ready_task = asyncio.create_task(llm_client.ensure_model_loaded())
        rag_task = asyncio.to_thread(indexer.search_chunks, announcement_id, message, RAG_TOP_K)

        session = await session_task
        mark("session_loaded")
        if await cancel_if_requested("session_loaded"):
            llm_ready_task.cancel()
            return 0
        if session is None:
            llm_ready_task.cancel()
            await asyncio.to_thread(generation_store.update, thread_id, status="failed", stage="session_missing", error_message="세션을 찾을 수 없습니다", finished=True)
            emit("error", {"detail": "세션을 찾을 수 없습니다"})
            return 0
        emit("status", {"stage": "session_loaded"})
        await asyncio.to_thread(generation_store.update, thread_id, status="running", stage="session_loaded", started=True)

        if session.get("announcement_id") is not None and session.get("announcement_id") != announcement_id:
            llm_ready_task.cancel()
            await asyncio.to_thread(generation_store.update, thread_id, status="failed", stage="announcement_mismatch", error_message="세션의 announcement_id와 요청값이 다릅니다", finished=True)
            emit("error", {"detail": "세션의 announcement_id와 요청값이 다릅니다"})
            return 0

        full_history_messages = [
            item for item in session.get("messages", [])
            if item.get("role") in {"user", "assistant"}
        ]

        summary_text = (session.get("summary_text") or "").strip()
        recent_window_size = max(LLM_HISTORY_MAX_TURNS * 2, 2)
        summary_recent_size = max(LLM_SUMMARY_RECENT_MESSAGES, recent_window_size)
        context_history_messages = full_history_messages[-recent_window_size:]

        if len(full_history_messages) > LLM_SUMMARY_TRIGGER_MESSAGES:
            old_messages = full_history_messages[:-summary_recent_size]
            latest_summary = _build_history_summary(old_messages, LLM_SUMMARY_MAX_CHARS)
            if latest_summary:
                summary_text = _merge_summary(summary_text, latest_summary, LLM_SUMMARY_MAX_CHARS)
                await asyncio.to_thread(store.save_summary, thread_id, summary_text)
            context_history_messages = full_history_messages[-summary_recent_size:]

        mark("history_optimized")
        emit("status", {"stage": "history_optimized"})
        await asyncio.to_thread(generation_store.update, thread_id, status="running", stage="history_optimized")
        if await cancel_if_requested("history_optimized"):
            llm_ready_task.cancel()
            return 0

        chunks, _ = await asyncio.gather(rag_task, llm_ready_task)
        indexer = None
        _release_cuda_cache()
        mark("rag_searched")
        emit("status", {"stage": "rag_searched", "chunk_count": len(chunks)})
        await asyncio.to_thread(generation_store.update, thread_id, status="running", stage="rag_searched")
        mark("llm_model_ready")
        emit("status", {"stage": "llm_model_ready"})
        await asyncio.to_thread(generation_store.update, thread_id, status="running", stage="llm_model_ready")
        if await cancel_if_requested("llm_model_ready"):
            return 0

        if chunks:
            rag_context = "\n\n".join(
                f"[chunk:{idx}]\n{chunk.page_content}"
                for idx, chunk in enumerate(chunks, start=1)
            )
            if len(rag_context) > RAG_CONTEXT_MAX_CHARS:
                rag_context = rag_context[:RAG_CONTEXT_MAX_CHARS]
            mark("rag_context_prepared")
            emit("status", {"stage": "rag_context_prepared"})
            await asyncio.to_thread(generation_store.update, thread_id, status="running", stage="rag_context_prepared")
        else:
            announcement_text = await asyncio.to_thread(storage.get_processed_text, announcement_id)
            if announcement_text is None:
                await asyncio.to_thread(generation_store.update, thread_id, status="failed", stage="announcement_missing", error_message="공고 파일을 찾을 수 없습니다", finished=True)
                emit("error", {"detail": "공고 파일을 찾을 수 없습니다"})
                return 0
            rag_context, fallback_count = _build_keyword_fallback_context(
                announcement_text,
                message,
                RAG_CONTEXT_MAX_CHARS,
                RAG_TOP_K,
            )
            mark("fallback_context_loaded")
            emit("status", {"stage": "fallback_context_loaded", "snippet_count": fallback_count})
            await asyncio.to_thread(generation_store.update, thread_id, status="running", stage="fallback_context_loaded")

        if not rag_context.strip():
            rag_context = ""

        await wait_for_gpu_capacity()
        if await cancel_if_requested("gpu_ready"):
            return 0
        await asyncio.to_thread(generation_store.update, thread_id, status="running", stage="llm_generating")

        full_response = ""
        full_thinking = ""
        llm_internal_timings: dict = {}
        llm_log: dict = {}
        async for item in llm_client.rag_chat_stream(
            question=message,
            context=rag_context,
            history=context_history_messages,
            summary_text=summary_text,
        ):
            item_type = item.get("type")
            if item_type == "thinking":
                thinking = item.get("content", "")
                full_thinking += thinking
                emit("thinking", {"text": thinking})
                now = time.monotonic()
                if now - last_thinking_save_at >= 0.5:
                    await asyncio.to_thread(
                        generation_store.update,
                        thread_id,
                        status="running",
                        stage="llm_thinking",
                        thinking_response=full_thinking,
                    )
                    last_thinking_save_at = now
                if await cancel_if_requested("llm_thinking"):
                    return 0
            elif item_type == "token":
                token = item.get("content", "")
                full_response += token
                emit("token", {"text": token})
                now = time.monotonic()
                if now - last_partial_save_at >= 0.5:
                    await asyncio.to_thread(
                        generation_store.update,
                        thread_id,
                        status="running",
                        stage="llm_generating",
                        partial_response=full_response,
                    )
                    last_partial_save_at = now
                if await cancel_if_requested("llm_generating"):
                    return 0
            elif item_type == "final":
                full_response = item.get("content", "")
                full_thinking = item.get("thinking", full_thinking)
                llm_internal_timings = item.get("internal_timings", {})
                llm_log = item.get("raw_metadata", {})
                await asyncio.to_thread(
                    generation_store.update,
                    thread_id,
                    status="running",
                    stage="llm_response_generated",
                    thinking_response=full_thinking,
                    partial_response=full_response,
                )

        mark("llm_response_generated")
        emit("status", {"stage": "llm_response_generated"})

        updated_messages = full_history_messages + [
            {"role": "user", "content": message},
            {"role": "assistant", "content": full_response},
        ]
        saved = await asyncio.to_thread(store.save_session, thread_id, updated_messages, announcement_id)
        mark("session_saved")
        if not saved:
            await asyncio.to_thread(generation_store.update, thread_id, status="failed", stage="session_save_failed", error_message="세션 저장에 실패했습니다", finished=True)
            emit("error", {"detail": "세션 저장에 실패했습니다"})
            return 0
        emit("status", {"stage": "session_saved"})
        await asyncio.to_thread(generation_store.update, thread_id, status="completed", stage="session_saved", partial_response=full_response, finished=True)

        mark("response_ready")
        emit(
            "done",
            {
                "response": full_response,
                "thinking": full_thinking,
                "time_spend": build_time_spend(llm_internal_timings),
                "log": {"metadata": _safe_serialize(llm_log) if llm_log else {}, "server_log": []},
            },
        )
        await asyncio.to_thread(generation_store.finish, thread_id)
        return 0
    except (RequestError, ResponseError):
        traceback.print_exc(file=sys.stderr)
        await asyncio.to_thread(generation_store.update, thread_id, status="failed", stage="ollama_error", error_message="Ollama 호출 중 오류가 발생했습니다.", finished=True)
        emit("error", {"detail": "Ollama 호출 중 오류가 발생했습니다."})
        await asyncio.to_thread(generation_store.finish, thread_id)
        return 0
    except ValueError:
        traceback.print_exc(file=sys.stderr)
        await asyncio.to_thread(generation_store.update, thread_id, status="failed", stage="empty_response", error_message="Ollama 응답이 비어 있습니다.", finished=True)
        emit("error", {"detail": "Ollama 응답이 비어 있습니다."})
        await asyncio.to_thread(generation_store.finish, thread_id)
        return 0
    except Exception:
        traceback.print_exc(file=sys.stderr)
        try:
            await asyncio.to_thread(generation_store.update, thread_id, status="failed", stage="chat_error", error_message="채팅 처리 중 오류가 발생했습니다.", finished=True)
        except Exception:
            pass
        emit("error", {"detail": "채팅 처리 중 오류가 발생했습니다."})
        await asyncio.to_thread(generation_store.finish, thread_id)
        return 0


def llm_chat(_args: argparse.Namespace) -> int:
    return asyncio.run(_llm_chat_async())


def main() -> int:
    parser = argparse.ArgumentParser(description="Starting Block AI/RAG command bridge")
    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("validation").set_defaults(func=validation)
    subparsers.add_parser("upload").set_defaults(func=upload)
    get_parser = subparsers.add_parser("get-announcement")
    get_parser.add_argument("--id", required=True)
    get_parser.set_defaults(func=get_announcement)

    subparsers.add_parser("delete-announcement").set_defaults(func=delete_announcement)
    start_parser = subparsers.add_parser("llm-start")
    start_parser.add_argument("--user-id", type=int, default=None)
    start_parser.set_defaults(func=llm_start)
    subparsers.add_parser("llm-chat").set_defaults(func=llm_chat)
    subparsers.add_parser("llm-mark-queued").set_defaults(func=llm_mark_queued)
    cancel_parser = subparsers.add_parser("llm-cancel")
    cancel_parser.add_argument("--thread-id", required=True)
    cancel_parser.add_argument("--reason", default="user_cancelled")
    cancel_parser.set_defaults(func=llm_cancel)
    status_parser = subparsers.add_parser("llm-status")
    status_parser.add_argument("--thread-id", required=True)
    status_parser.set_defaults(func=llm_status)
    history_parser = subparsers.add_parser("llm-history")
    history_parser.add_argument("--thread-id", required=True)
    history_parser.set_defaults(func=llm_history)
    events_parser = subparsers.add_parser("llm-events")
    events_parser.add_argument("--thread-id", required=True)
    events_parser.add_argument("--after-seq", default="0")
    events_parser.set_defaults(func=llm_events)
    delete_parser = subparsers.add_parser("llm-delete")
    delete_parser.add_argument("--thread-id", required=True)
    delete_parser.set_defaults(func=llm_delete)

    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
