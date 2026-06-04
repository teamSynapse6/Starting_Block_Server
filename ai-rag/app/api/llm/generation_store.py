import json
from datetime import datetime, timezone
from typing import Any

import redis

from app.core.config import (
    LLM_GENERATION_FINISHED_TTL_SECONDS,
    LLM_GENERATION_STATUS_TTL_SECONDS,
    REDIS_URL,
)


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


class RedisGenerationStore:
    def __init__(self):
        self.client = redis.Redis.from_url(REDIS_URL, decode_responses=True)

    def _key(self, thread_id: str) -> str:
        return f"llm:generation:{thread_id}"

    def _events_key(self, thread_id: str) -> str:
        return f"llm:generation:{thread_id}:events"

    def _event_seq_key(self, thread_id: str) -> str:
        return f"llm:generation:{thread_id}:event_seq"

    def start(self, thread_id: str, announcement_id: int, message: str) -> dict[str, Any]:
        now = _now()
        payload = {
            "thread_id": thread_id,
            "announcement_id": announcement_id,
            "status": "queued",
            "stage": "queued",
            "message": message,
            "thinking_response": "",
            "partial_response": "",
            "error_message": "",
            "cancel_requested": False,
            "cancel_reason": "",
            "created_at": now,
            "started_at": "",
            "updated_at": now,
            "finished_at": "",
        }
        self._set(thread_id, payload)
        self.client.delete(self._events_key(thread_id), self._event_seq_key(thread_id))
        return payload

    def update(
        self,
        thread_id: str,
        *,
        status: str | None = None,
        stage: str | None = None,
        partial_response: str | None = None,
        thinking_response: str | None = None,
        error_message: str | None = None,
        cancel_requested: bool | None = None,
        cancel_reason: str | None = None,
        started: bool = False,
        finished: bool = False,
    ) -> dict[str, Any]:
        payload = self.get(thread_id) or {
            "thread_id": thread_id,
            "status": "queued",
            "stage": "queued",
            "thinking_response": "",
            "partial_response": "",
            "error_message": "",
            "cancel_requested": False,
            "cancel_reason": "",
            "created_at": _now(),
            "started_at": "",
            "finished_at": "",
        }
        now = _now()
        if status is not None:
            payload["status"] = status
        if stage is not None:
            payload["stage"] = stage
        if partial_response is not None:
            payload["partial_response"] = partial_response
        if thinking_response is not None:
            payload["thinking_response"] = thinking_response
        if error_message is not None:
            payload["error_message"] = error_message
        if cancel_requested is not None:
            payload["cancel_requested"] = cancel_requested
        if cancel_reason is not None:
            payload["cancel_reason"] = cancel_reason
        if started and not payload.get("started_at"):
            payload["started_at"] = now
        if finished:
            payload["finished_at"] = now
        payload["updated_at"] = now
        self._set(thread_id, payload)
        if finished:
            self._expire_finished(thread_id)
        return payload

    def append_event(self, thread_id: str, event: str, payload: dict[str, Any]) -> dict[str, Any]:
        seq = int(self.client.incr(self._event_seq_key(thread_id)))
        item = {
            "seq": seq,
            "event": event,
            "data": payload,
            "created_at": _now(),
        }
        pipe = self.client.pipeline()
        pipe.rpush(self._events_key(thread_id), json.dumps(item, ensure_ascii=False))
        pipe.expire(self._events_key(thread_id), LLM_GENERATION_STATUS_TTL_SECONDS)
        pipe.expire(self._event_seq_key(thread_id), LLM_GENERATION_STATUS_TTL_SECONDS)
        pipe.execute()
        return item

    def events_after(self, thread_id: str, after_seq: int = 0) -> list[dict[str, Any]]:
        start = max(after_seq, 0)
        rows = self.client.lrange(self._events_key(thread_id), start, -1)
        items: list[dict[str, Any]] = []
        for row in rows:
            try:
                item = json.loads(row)
            except json.JSONDecodeError:
                continue
            seq = int(item.get("seq") or 0)
            if seq > after_seq:
                items.append(item)
        return items

    def finish(self, thread_id: str) -> None:
        self._expire_finished(thread_id)

    def request_cancel(self, thread_id: str, reason: str = "user_cancelled") -> dict[str, Any]:
        return self.update(
            thread_id,
            status="cancelling",
            stage="cancel_requested",
            cancel_requested=True,
            cancel_reason=reason,
        )

    def mark_cancelled(self, thread_id: str, reason: str = "user_cancelled") -> dict[str, Any]:
        return self.update(
            thread_id,
            status="cancelled",
            stage="cancelled",
            cancel_requested=True,
            cancel_reason=reason,
            finished=True,
        )

    def is_cancel_requested(self, thread_id: str) -> bool:
        payload = self.get(thread_id)
        if not payload:
            return False
        return bool(payload.get("cancel_requested")) or payload.get("status") in {"cancelling", "cancelled"}

    def get(self, thread_id: str) -> dict[str, Any] | None:
        raw = self.client.get(self._key(thread_id))
        if not raw:
            return None
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            return None

    def delete(self, thread_id: str) -> None:
        self.client.delete(self._key(thread_id), self._events_key(thread_id), self._event_seq_key(thread_id))

    def _set(self, thread_id: str, payload: dict[str, Any]) -> None:
        self.client.setex(
            self._key(thread_id),
            LLM_GENERATION_STATUS_TTL_SECONDS,
            json.dumps(payload, ensure_ascii=False),
        )

    def _expire_finished(self, thread_id: str) -> None:
        ttl = max(LLM_GENERATION_FINISHED_TTL_SECONDS, 1)
        self.client.expire(self._key(thread_id), ttl)
        self.client.expire(self._events_key(thread_id), ttl)
        self.client.expire(self._event_seq_key(thread_id), ttl)
