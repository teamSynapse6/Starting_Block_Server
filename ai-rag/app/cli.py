import argparse
import asyncio
import json
import os
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


def _sse(event: str, payload: dict[str, Any]) -> str:
    return f"event: {event}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"


def llm_start(_args: argparse.Namespace) -> int:
    from app.api.llm.session_store import MySQLSessionStore
    from app.core.db_models import ensure_database_and_tables

    try:
        store = MySQLSessionStore()
        try:
            thread_id = store.create_session()
        except Exception:
            ensure_database_and_tables()
            thread_id = store.create_session()
        return _json_stdout({"thread_id": thread_id})
    except Exception as error:
        traceback.print_exc(file=sys.stderr)
        return _json_error(f"세션을 생성하는 동안 오류가 발생했습니다: {error}")


def llm_delete(args: argparse.Namespace) -> int:
    from app.api.llm.archive_store import MySQLArchiveStore
    from app.api.llm.session_store import MySQLSessionStore

    thread_id = args.thread_id
    if not thread_id:
        return _json_error("thread_id 파라미터가 필요합니다.")

    store = MySQLSessionStore()
    archive_store = MySQLArchiveStore()

    session = store.get_session(thread_id)
    if session is None:
        return _json_stdout({"id": thread_id, "deleted": False})

    try:
        archive_store.archive_session(session)
        return _json_stdout({"id": thread_id, "deleted": True})
    except Exception as error:
        return _json_error(f"세션 삭제 중 오류가 발생했습니다: {error}")


async def _llm_chat_async() -> int:
    from ollama import RequestError, ResponseError

    from app.api.announcement.vector_indexer import AnnouncementVectorIndexer
    from app.api.llm.client import OllamaClient
    from app.api.llm.session_store import MySQLSessionStore
    from app.core.config import (
        EMBEDDING_DEVICE,
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
    storage = MinioStorage()
    storage.ensure_bucket()
    indexer = AnnouncementVectorIndexer(EMBEDDING_DEVICE)
    indexer.ensure_ready()
    ollama_client = OllamaClient()

    started_at = time.perf_counter()
    checkpoints: list[tuple[str, float]] = [("request_received", started_at)]

    def emit(event: str, payload_item: dict[str, Any]):
        sys.stdout.write(_sse(event, payload_item))
        sys.stdout.flush()

    def mark(name: str):
        checkpoints.append((name, time.perf_counter()))

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

    try:
        announcement_id = int(announcement_id)
        emit("status", {"stage": "request_received"})

        session_task = asyncio.to_thread(store.get_session, thread_id)
        ollama_task = asyncio.create_task(ollama_client.ensure_model_loaded())
        rag_task = asyncio.to_thread(indexer.search_chunks, announcement_id, message, RAG_TOP_K)

        session = await session_task
        mark("session_loaded")
        if session is None:
            ollama_task.cancel()
            emit("error", {"detail": "세션을 찾을 수 없습니다"})
            return 0
        emit("status", {"stage": "session_loaded"})

        if session.get("announcement_id") is not None and session.get("announcement_id") != announcement_id:
            ollama_task.cancel()
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

        chunks, _ = await asyncio.gather(rag_task, ollama_task)
        mark("rag_searched")
        emit("status", {"stage": "rag_searched", "chunk_count": len(chunks)})
        mark("ollama_model_ready")
        emit("status", {"stage": "ollama_model_ready"})

        if chunks:
            rag_context = "\n\n".join(chunk.page_content for chunk in chunks)
            if len(rag_context) > RAG_CONTEXT_MAX_CHARS:
                rag_context = rag_context[:RAG_CONTEXT_MAX_CHARS]
            mark("rag_context_prepared")
            emit("status", {"stage": "rag_context_prepared"})
        else:
            announcement_text = await asyncio.to_thread(storage.get_processed_text, announcement_id)
            if announcement_text is None:
                emit("error", {"detail": "공고 파일을 찾을 수 없습니다"})
                return 0
            rag_context = announcement_text[:RAG_CONTEXT_MAX_CHARS]
            mark("fallback_context_loaded")
            emit("status", {"stage": "fallback_context_loaded"})

        if not rag_context.strip():
            emit("error", {"detail": "공고 파일을 찾을 수 없습니다"})
            return 0

        full_response = ""
        ollama_internal_timings: dict = {}
        ollama_log: dict = {}
        async for item in ollama_client.rag_chat_stream(
            question=message,
            context=rag_context,
            history=context_history_messages,
            summary_text=summary_text,
        ):
            item_type = item.get("type")
            if item_type == "token":
                token = item.get("content", "")
                full_response += token
                emit("token", {"text": token})
            elif item_type == "final":
                full_response = item.get("content", "")
                ollama_internal_timings = item.get("internal_timings", {})
                ollama_log = item.get("raw_metadata", {})

        mark("ollama_response_generated")
        emit("status", {"stage": "ollama_response_generated"})

        updated_messages = full_history_messages + [
            {"role": "user", "content": message},
            {"role": "assistant", "content": full_response},
        ]
        saved = await asyncio.to_thread(store.save_session, thread_id, updated_messages, announcement_id)
        mark("session_saved")
        if not saved:
            emit("error", {"detail": "세션 저장에 실패했습니다"})
            return 0
        emit("status", {"stage": "session_saved"})

        mark("response_ready")
        emit(
            "done",
            {
                "response": full_response,
                "time_spend": build_time_spend(ollama_internal_timings),
                "log": {"metadata": _safe_serialize(ollama_log) if ollama_log else {}, "server_log": []},
            },
        )
        return 0
    except (RequestError, ResponseError):
        traceback.print_exc(file=sys.stderr)
        emit("error", {"detail": "Ollama 호출 중 오류가 발생했습니다."})
        return 0
    except ValueError:
        traceback.print_exc(file=sys.stderr)
        emit("error", {"detail": "Ollama 응답이 비어 있습니다."})
        return 0
    except Exception:
        traceback.print_exc(file=sys.stderr)
        emit("error", {"detail": "채팅 처리 중 오류가 발생했습니다."})
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
    subparsers.add_parser("llm-start").set_defaults(func=llm_start)
    subparsers.add_parser("llm-chat").set_defaults(func=llm_chat)
    delete_parser = subparsers.add_parser("llm-delete")
    delete_parser.add_argument("--thread-id", required=True)
    delete_parser.set_defaults(func=llm_delete)

    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
