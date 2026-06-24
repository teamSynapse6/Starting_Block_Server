import time
from datetime import datetime, timezone
from typing import Any

import redis

from app.core.config import REDIS_URL


class AnnouncementIndexJobStore:
    def __init__(self):
        self.redis = redis.Redis.from_url(REDIS_URL, decode_responses=True)
        self.prefix = "announcement_index_jobs"
        self.queued_key = f"{self.prefix}:queued"
        self.processing_key = f"{self.prefix}:processing"
        self.failed_key = f"{self.prefix}:failed"
        self.active_key = f"{self.prefix}:active"
        self.id_key = f"{self.prefix}:id"

    def _now(self) -> str:
        return datetime.now(timezone.utc).isoformat()

    def _job_key(self, job_id: int | str) -> str:
        return f"{self.prefix}:job:{job_id}"

    def _active_member(self, action: str, announcement_id: int) -> str:
        return f"{action}:{announcement_id}"

    def _normalize_action(self, action: str) -> str:
        normalized_action = action.strip().lower()
        if normalized_action not in {"upsert", "delete"}:
            raise ValueError("지원하지 않는 인덱싱 작업입니다.")
        return normalized_action

    def enqueue(self, action: str, announcement_id: int) -> None:
        normalized_action = self._normalize_action(action)
        announcement_id = int(announcement_id)
        active_member = self._active_member(normalized_action, announcement_id)

        if not self.redis.sadd(self.active_key, active_member):
            return

        job_id = int(self.redis.incr(self.id_key))
        job = {
            "id": str(job_id),
            "action": normalized_action,
            "announcement_id": str(announcement_id),
            "status": "queued",
            "attempts": "0",
            "worker_id": "",
            "last_error": "",
            "created_at": self._now(),
            "started_at": "",
            "finished_at": "",
        }
        pipe = self.redis.pipeline()
        pipe.hset(self._job_key(job_id), mapping=job)
        pipe.rpush(self.queued_key, job_id)
        pipe.execute()

    def requeue_stale_processing(self, stale_seconds: int = 3600) -> int:
        if stale_seconds <= 0:
            return 0

        cutoff = time.time() - stale_seconds
        job_ids = self.redis.zrangebyscore(self.processing_key, 0, cutoff)
        count = 0
        for job_id in job_ids:
            job_key = self._job_key(job_id)
            job = self.redis.hgetall(job_key)
            if not job or job.get("status") != "processing":
                self.redis.zrem(self.processing_key, job_id)
                continue

            pipe = self.redis.pipeline()
            pipe.hset(
                job_key,
                mapping={
                    "status": "queued",
                    "worker_id": "",
                    "last_error": "stale processing job requeued",
                    "started_at": "",
                },
            )
            pipe.zrem(self.processing_key, job_id)
            pipe.rpush(self.queued_key, job_id)
            pipe.execute()
            count += 1
        return count

    def requeue_failed(self, announcement_ids: list[int] | None = None) -> int:
        target_ids = {int(item) for item in announcement_ids} if announcement_ids else None
        count = 0
        for job_id in self.redis.smembers(self.failed_key):
            job_key = self._job_key(job_id)
            job = self.redis.hgetall(job_key)
            if not job:
                self.redis.srem(self.failed_key, job_id)
                continue
            announcement_id = int(job.get("announcement_id") or 0)
            if target_ids is not None and announcement_id not in target_ids:
                continue

            action = self._normalize_action(job.get("action", "upsert"))
            active_member = self._active_member(action, announcement_id)
            if not self.redis.sadd(self.active_key, active_member):
                continue

            pipe = self.redis.pipeline()
            pipe.hset(
                job_key,
                mapping={
                    "status": "queued",
                    "worker_id": "",
                    "last_error": "",
                    "started_at": "",
                    "finished_at": "",
                },
            )
            pipe.srem(self.failed_key, job_id)
            pipe.rpush(self.queued_key, job_id)
            pipe.execute()
            count += 1
        return count

    def claim_next(self, worker_id: str) -> dict[str, Any] | None:
        jobs = self.claim_batch(worker_id, 1)
        return jobs[0] if jobs else None

    def claim_batch(self, worker_id: str, limit: int) -> list[dict[str, Any]]:
        limit = max(int(limit), 1)
        jobs: list[dict[str, Any]] = []

        for _ in range(limit):
            job_id = self.redis.lpop(self.queued_key)
            if not job_id:
                break

            job_key = self._job_key(job_id)
            job = self.redis.hgetall(job_key)
            if not job or job.get("status") != "queued":
                continue

            attempts = int(job.get("attempts") or 0) + 1
            pipe = self.redis.pipeline()
            pipe.hset(
                job_key,
                mapping={
                    "status": "processing",
                    "worker_id": worker_id,
                    "attempts": str(attempts),
                    "started_at": self._now(),
                },
            )
            pipe.zadd(self.processing_key, {job_id: time.time()})
            pipe.execute()

            jobs.append(
                {
                    "id": int(job_id),
                    "action": job["action"],
                    "announcement_id": int(job["announcement_id"]),
                    "attempts": attempts,
                }
            )

        return jobs

    def mark_done(self, job_id: int) -> None:
        job_id = int(job_id)
        job_key = self._job_key(job_id)
        job = self.redis.hgetall(job_key)
        if not job:
            self.redis.zrem(self.processing_key, job_id)
            return

        active_member = self._active_member(job.get("action", ""), int(job.get("announcement_id") or 0))
        pipe = self.redis.pipeline()
        pipe.hset(job_key, mapping={"status": "done", "last_error": "", "finished_at": self._now()})
        pipe.zrem(self.processing_key, job_id)
        pipe.srem(self.active_key, active_member)
        pipe.expire(job_key, 86400)
        pipe.execute()

    def mark_failed(self, job_id: int, error_message: str) -> None:
        job_id = int(job_id)
        job_key = self._job_key(job_id)
        job = self.redis.hgetall(job_key)
        if not job:
            self.redis.zrem(self.processing_key, job_id)
            return

        active_member = self._active_member(job.get("action", ""), int(job.get("announcement_id") or 0))
        pipe = self.redis.pipeline()
        pipe.hset(
            job_key,
            mapping={
                "status": "failed",
                "last_error": error_message[:2000],
                "finished_at": self._now(),
            },
        )
        pipe.zrem(self.processing_key, job_id)
        pipe.srem(self.active_key, active_member)
        pipe.sadd(self.failed_key, job_id)
        pipe.execute()

    def clear_all(self) -> int:
        keys = list(self.redis.scan_iter(f"{self.prefix}:*"))
        if not keys:
            return 0
        return int(self.redis.delete(*keys))
