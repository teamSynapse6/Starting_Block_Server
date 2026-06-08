import uuid
from contextlib import contextmanager
from datetime import datetime
from urllib.parse import quote_plus
from zoneinfo import ZoneInfo

from sqlalchemy import Column, DateTime, ForeignKey, Index, Integer, String, Text, UniqueConstraint, create_engine, text
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import declarative_base, relationship, sessionmaker

from app.core.config import (
    MYSQL_CHARSET,
    MYSQL_DB_NAME,
    MYSQL_HOST,
    MYSQL_PASSWORD,
    MYSQL_PORT,
    MYSQL_USER,
)


KST = ZoneInfo("Asia/Seoul")


def kst_now() -> datetime:
    return datetime.now(KST).replace(tzinfo=None)


def _server_database_url() -> str:
    encoded_user = quote_plus(MYSQL_USER)
    encoded_password = quote_plus(MYSQL_PASSWORD)
    return f"mysql+pymysql://{encoded_user}:{encoded_password}@{MYSQL_HOST}:{MYSQL_PORT}/?charset={MYSQL_CHARSET}"


def _app_database_url() -> str:
    encoded_user = quote_plus(MYSQL_USER)
    encoded_password = quote_plus(MYSQL_PASSWORD)
    return f"mysql+pymysql://{encoded_user}:{encoded_password}@{MYSQL_HOST}:{MYSQL_PORT}/{MYSQL_DB_NAME}?charset={MYSQL_CHARSET}"


def ensure_database_exists():
    engine = create_engine(_server_database_url(), pool_pre_ping=True)
    try:
        with engine.begin() as connection:
            connection.execute(text(f"CREATE DATABASE IF NOT EXISTS `{MYSQL_DB_NAME}` CHARACTER SET {MYSQL_CHARSET}"))
    except SQLAlchemyError:
        return


Base = declarative_base()


class LLMThread(Base):
    __tablename__ = "llm_threads"

    thread_id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(Integer, nullable=True, index=True)
    announcement_id = Column(Integer, nullable=True)
    status = Column(String(16), nullable=False, default="active", index=True)
    created_at = Column(DateTime(timezone=True), nullable=False, default=kst_now)
    last_activity = Column(DateTime(timezone=True), nullable=False, default=kst_now)
    summary_text = Column(Text, nullable=True)
    summary_updated_at = Column(DateTime(timezone=True), nullable=True)
    archived_at = Column(DateTime(timezone=True), nullable=True)

    messages = relationship("LLMMessage", back_populates="thread", cascade="all, delete-orphan", passive_deletes=True)

    __table_args__ = (
        Index("ix_llm_threads_status_last_activity", "status", "last_activity"),
        Index("ix_llm_threads_user_status_last_activity", "user_id", "status", "last_activity"),
    )


class LLMMessage(Base):
    __tablename__ = "llm_messages"

    id = Column(Integer, primary_key=True, autoincrement=True)
    thread_id = Column(String(36), ForeignKey("llm_threads.thread_id", ondelete="CASCADE"), nullable=False, index=True)
    seq = Column(Integer, nullable=False)
    role = Column(String(20), nullable=False)
    content = Column(Text, nullable=False)
    compute_type = Column(String(32), nullable=True)
    model_name = Column(String(128), nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=False, default=kst_now)

    thread = relationship("LLMThread", back_populates="messages")

    __table_args__ = (UniqueConstraint("thread_id", "seq", name="uq_llm_messages_thread_seq"),)


class AnnouncementIndexJob(Base):
    __tablename__ = "announcement_index_jobs"

    id = Column(Integer, primary_key=True, autoincrement=True)
    action = Column(String(16), nullable=False, index=True)
    announcement_id = Column(Integer, nullable=False, index=True)
    status = Column(String(16), nullable=False, default="queued", index=True)
    attempts = Column(Integer, nullable=False, default=0)
    worker_id = Column(String(64), nullable=True)
    last_error = Column(Text, nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=False, default=kst_now)
    started_at = Column(DateTime(timezone=True), nullable=True)
    finished_at = Column(DateTime(timezone=True), nullable=True)

    __table_args__ = (
        Index("ix_index_jobs_status_created_at", "status", "created_at"),
    )


engine = create_engine(
    _app_database_url(),
    pool_pre_ping=True,
    connect_args={"init_command": "SET time_zone = '+09:00'"},
)
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False)


def ensure_database_and_tables():
    ensure_database_exists()
    Base.metadata.create_all(bind=engine)
    _ensure_llm_thread_columns()


def _ensure_llm_thread_columns():
    with engine.begin() as connection:
        columns_to_ensure = {
            "user_id": "ALTER TABLE llm_threads ADD COLUMN user_id BIGINT NULL",
            "summary_text": "ALTER TABLE llm_threads ADD COLUMN summary_text TEXT NULL",
            "summary_updated_at": "ALTER TABLE llm_threads ADD COLUMN summary_updated_at DATETIME NULL",
        }

        for column_name, ddl in columns_to_ensure.items():
            exists = connection.execute(
                text(
                    """
                    SELECT COUNT(*)
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = :schema
                      AND TABLE_NAME = 'llm_threads'
                      AND COLUMN_NAME = :column_name
                    """
                ),
                {"schema": MYSQL_DB_NAME, "column_name": column_name},
            ).scalar()

            if int(exists or 0) == 0:
                connection.execute(text(ddl))

        message_columns_to_ensure = {
            "compute_type": "ALTER TABLE llm_messages ADD COLUMN compute_type VARCHAR(32) NULL",
            "model_name": "ALTER TABLE llm_messages ADD COLUMN model_name VARCHAR(128) NULL",
        }

        for column_name, ddl in message_columns_to_ensure.items():
            exists = connection.execute(
                text(
                    """
                    SELECT COUNT(*)
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = :schema
                      AND TABLE_NAME = 'llm_messages'
                      AND COLUMN_NAME = :column_name
                    """
                ),
                {"schema": MYSQL_DB_NAME, "column_name": column_name},
            ).scalar()

            if int(exists or 0) == 0:
                connection.execute(text(ddl))

        indexes_to_ensure = {
            "ix_llm_threads_user_status_last_activity": (
                "CREATE INDEX ix_llm_threads_user_status_last_activity "
                "ON llm_threads (user_id, status, last_activity)"
            ),
        }

        for index_name, ddl in indexes_to_ensure.items():
            exists = connection.execute(
                text(
                    """
                    SELECT COUNT(*)
                    FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = :schema
                      AND TABLE_NAME = 'llm_threads'
                      AND INDEX_NAME = :index_name
                    """
                ),
                {"schema": MYSQL_DB_NAME, "index_name": index_name},
            ).scalar()

            if int(exists or 0) == 0:
                connection.execute(text(ddl))


@contextmanager
def get_db_session():
    session = SessionLocal()
    try:
        yield session
    finally:
        session.close()
