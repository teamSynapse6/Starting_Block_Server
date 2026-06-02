import argparse
import asyncio
import json
import os
import sys
import tempfile
from typing import Any

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


def _json_stdout(payload: dict[str, Any]) -> int:
    print(json.dumps(payload, ensure_ascii=False))
    return 0


def _json_error(message: str) -> int:
    print(json.dumps({"status": "failed", "error": message}, ensure_ascii=False), file=sys.stderr)
    return 1


def validation(_args: argparse.Namespace) -> int:
    storage = MinioStorage()
    storage.ensure_bucket()
    return _json_stdout({"file_ids": storage.list_processed_ids()})


def upload(_args: argparse.Namespace) -> int:
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


def main() -> int:
    parser = argparse.ArgumentParser(description="Starting Block AI/RAG command bridge")
    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("validation").set_defaults(func=validation)
    subparsers.add_parser("upload").set_defaults(func=upload)

    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
