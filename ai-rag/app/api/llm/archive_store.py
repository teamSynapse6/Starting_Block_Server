from datetime import datetime

from app.core.db_models import LLMThread, get_db_session, kst_now


class MySQLArchiveStore:
    def __init__(self):
        pass

    def initialize(self):
        return None

    def archive_session(self, session: dict):
        thread_id = session.get("thread_id")
        if not thread_id:
            return

        last_activity_raw = session.get("last_activity")
        try:
            last_activity = datetime.fromisoformat(last_activity_raw) if last_activity_raw else kst_now()
        except Exception:
            last_activity = kst_now()

        with get_db_session() as db:
            thread = db.query(LLMThread).filter(LLMThread.thread_id == thread_id).first()
            if thread is None:
                return

            thread.status = "archived"
            thread.last_activity = last_activity
            thread.archived_at = kst_now()
            db.commit()
