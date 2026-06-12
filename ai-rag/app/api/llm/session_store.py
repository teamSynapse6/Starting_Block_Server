import uuid

from app.core.db_models import LLMMessage, LLMThread, get_db_session, kst_now


class MySQLSessionStore:
    def __init__(self):
        pass

    def _now(self) -> str:
        return kst_now().isoformat()

    def create_session(self, user_id: int | None = None) -> str:
        thread_id = str(uuid.uuid4())

        with get_db_session() as db:
            thread = LLMThread(
                thread_id=thread_id,
                user_id=user_id,
                announcement_id=None,
                status="active",
                created_at=kst_now(),
                last_activity=kst_now(),
            )
            db.add(thread)
            db.commit()

        return thread_id

    def _serialize_thread(self, thread: LLMThread, messages: list[dict]) -> dict:
        return {
            "thread_id": thread.thread_id,
            "status": thread.status,
            "created_at": thread.created_at.isoformat() if thread.created_at else self._now(),
            "last_activity": thread.last_activity.isoformat() if thread.last_activity else self._now(),
            "announcement_id": thread.announcement_id,
            "summary_text": thread.summary_text or "",
            "messages": messages,
        }

    def get_session(self, thread_id: str) -> dict | None:
        return self.get_session_by_status(thread_id, "active")

    def get_session_any(self, thread_id: str) -> dict | None:
        return self.get_session_by_status(thread_id, None)

    def get_session_by_status(self, thread_id: str, status: str | None) -> dict | None:
        with get_db_session() as db:
            query = db.query(LLMThread).filter(LLMThread.thread_id == thread_id)
            if status is not None:
                query = query.filter(LLMThread.status == status)
            thread = query.first()
            if thread is None:
                return None

            rows = (
                db.query(LLMMessage)
                .filter(LLMMessage.thread_id == thread_id)
                .order_by(LLMMessage.seq.asc())
                .all()
            )
            messages = [
                {
                    "role": row.role,
                    "content": row.content,
                    "compute_type": row.compute_type,
                    "model_name": row.model_name,
                }
                for row in rows
            ]

        return self._serialize_thread(thread, messages)

    def save_session(self, thread_id: str, messages: list[dict], announcement_id: int | None):
        with get_db_session() as db:
            thread = db.query(LLMThread).filter(LLMThread.thread_id == thread_id, LLMThread.status == "active").first()
            if thread is None:
                return False

            thread.last_activity = kst_now()
            if thread.announcement_id is None:
                thread.announcement_id = announcement_id

            db.query(LLMMessage).filter(LLMMessage.thread_id == thread_id).delete(synchronize_session=False)

            seq = 1
            for message in messages:
                row = LLMMessage(
                    thread_id=thread_id,
                    seq=seq,
                    role=message.get("role", "user"),
                    content=message.get("content", ""),
                    compute_type=message.get("compute_type"),
                    model_name=message.get("model_name"),
                    created_at=kst_now(),
                )
                db.add(row)
                seq += 1

            db.commit()
            return True

    def append_message(
        self,
        thread_id: str,
        role: str,
        content: str,
        announcement_id: int | None,
        compute_type: str | None = None,
        model_name: str | None = None,
    ):
        with get_db_session() as db:
            thread = db.query(LLMThread).filter(LLMThread.thread_id == thread_id, LLMThread.status == "active").first()
            if thread is None:
                return False

            if thread.announcement_id is not None and announcement_id is not None and thread.announcement_id != announcement_id:
                return False

            thread.last_activity = kst_now()
            if thread.announcement_id is None:
                thread.announcement_id = announcement_id

            latest_seq = (
                db.query(LLMMessage.seq)
                .filter(LLMMessage.thread_id == thread_id)
                .order_by(LLMMessage.seq.desc())
                .first()
            )
            next_seq = int(latest_seq[0]) + 1 if latest_seq else 1

            row = LLMMessage(
                thread_id=thread_id,
                seq=next_seq,
                role=role,
                content=content,
                compute_type=compute_type,
                model_name=model_name,
                created_at=kst_now(),
            )
            db.add(row)
            db.commit()
            return True

    def delete_session(self, thread_id: str):
        with get_db_session() as db:
            db.query(LLMThread).filter(LLMThread.thread_id == thread_id).delete(synchronize_session=False)
            db.commit()

    def touch_session_activity(self, thread_id: str):
        with get_db_session() as db:
            thread = db.query(LLMThread).filter(LLMThread.thread_id == thread_id).first()
            if thread is None:
                return False

            thread.last_activity = kst_now()
            db.commit()
            return True

    def cancel_session(self, thread_id: str):
        return self.touch_session_activity(thread_id)

    def save_summary(self, thread_id: str, summary_text: str):
        with get_db_session() as db:
            thread = db.query(LLMThread).filter(LLMThread.thread_id == thread_id).first()
            if thread is None:
                return False

            thread.summary_text = summary_text
            thread.summary_updated_at = kst_now()
            db.commit()
            return True

    def list_session_ids(self) -> list[str]:
        with get_db_session() as db:
            rows = db.query(LLMThread.thread_id).filter(LLMThread.status == "active").all()
            return [row[0] for row in rows]
