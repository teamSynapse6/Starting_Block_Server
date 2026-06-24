import argparse
import os
import tempfile
from typing import Any

import httpx
from sqlalchemy import bindparam
from sqlalchemy import text as sql_text

from app.api.announcement.file_pipeline import (
    convert_image_paths_to_texts,
    convert_doc_path_to_text,
    convert_docx_bytes_to_text,
    convert_hwp_path_to_text,
    convert_hwpx_bytes_to_text,
    extract_pdf_text,
    is_image_format,
    normalize_file_format,
    render_office_bytes_to_image_paths,
    render_pdf_bytes_to_image_paths,
    resolve_downloaded_file_format,
    write_temp_image_bytes,
)
from app.api.announcement.index_job_store import AnnouncementIndexJobStore
from app.core.db_models import ensure_database_and_tables, get_db_session
from app.core.storage import MinioStorage


SUPPORTED_FORMATS = {"hwp", "hwpx", "pdf", "txt", "doc", "docx"}


def parse_args():
    parser = argparse.ArgumentParser(description="is_file_uploaded=false 공고 파일을 다시 전처리하고 인덱싱 job을 큐에 넣습니다.")
    parser.add_argument("--ids", type=str, default="", help="쉼표 구분 announcement id 목록. 미지정 시 DB의 미업로드 대상")
    parser.add_argument("--limit", type=int, default=0, help="처리 최대 개수. 0이면 제한 없음")
    parser.add_argument("--batch-size", type=int, default=20, help="로그 출력용 batch 크기")
    parser.add_argument("--timeout-seconds", type=float, default=60.0, help="파일 다운로드 timeout")
    return parser.parse_args()


def extract_extension(value: str | None) -> str | None:
    if not value:
        return None
    normalized = value.split("?", 1)[0].split("#", 1)[0]
    normalized = normalized.rsplit("/", 1)[-1].rsplit("\\", 1)[-1]
    if "." not in normalized:
        return None
    extension = normalized.rsplit(".", 1)[1].strip().lower()
    return normalize_file_format(extension) if extension else None


def resolve_extension(client: httpx.Client, url: str) -> str | None:
    try:
        response = client.head(url, follow_redirects=True)
        filename = response.headers.get("content-disposition", "")
        extension = extract_extension(filename)
        if extension:
            return extension
    except Exception:
        pass
    return extract_extension(url)


def is_supported(extension: str | None) -> bool:
    return bool(extension) and (extension in SUPPORTED_FORMATS or is_image_format(extension))


def load_targets(ids: list[int], limit: int) -> list[dict[str, Any]]:
    with get_db_session() as db:
        if ids:
            rows = db.execute(
                sql_text(
                    "SELECT id, file_url FROM announcement WHERE id IN :ids AND file_url IS NOT NULL AND file_url <> ''"
                ).bindparams(bindparam("ids", expanding=True)),
                {"ids": tuple(ids)},
            ).mappings().all()
        else:
            rows = db.execute(
                sql_text(
                    """
                    SELECT id, file_url
                    FROM announcement
                    WHERE file_url IS NOT NULL
                      AND file_url <> ''
                      AND (is_file_uploaded = false OR is_file_uploaded IS NULL)
                    ORDER BY id ASC
                    """
                )
            ).mappings().all()

    targets = [{"id": int(row["id"]), "url": str(row["file_url"])} for row in rows]
    if limit > 0:
        return targets[:limit]
    return targets


def process_target(
    client: httpx.Client,
    storage: MinioStorage,
    jobs: AnnouncementIndexJobStore,
    target: dict[str, Any],
    ocr_temp_dir: str,
    ocr_tasks: list[dict[str, Any]],
) -> str:
    announcement_id = int(target["id"])
    url = str(target["url"])
    expected_format = resolve_extension(client, url)

    temp_file_path = None
    try:
        response = client.get(url, follow_redirects=True)
        response.raise_for_status()
        file_bytes = response.content

        actual_format = resolve_downloaded_file_format(file_bytes, response.headers.get("content-disposition"))
        if actual_format not in SUPPORTED_FORMATS and not is_image_format(actual_format):
            return f"skipped unsupported extension={expected_format} actual={actual_format}"
        if is_supported(expected_format) and actual_format != expected_format:
            return f"failed expected={expected_format} actual={actual_format}"

        storage.put_raw_bytes(announcement_id, actual_format, file_bytes)
        if actual_format == "pdf":
            text = extract_pdf_text(file_bytes)
            if not text.strip():
                image_paths = render_pdf_bytes_to_image_paths(file_bytes, ocr_temp_dir, f"{announcement_id}_pdf")
                if image_paths:
                    ocr_tasks.append({"announcement_id": announcement_id, "image_paths": image_paths})
                    return "queued-ocr"
        elif actual_format == "hwp":
            try:
                with tempfile.NamedTemporaryFile(delete=False, suffix=".hwp") as temp_file:
                    temp_file.write(file_bytes)
                    temp_file_path = temp_file.name
                text = convert_hwp_path_to_text(temp_file_path)
            except Exception:
                text = ""
            if not text.strip():
                image_paths = render_office_bytes_to_image_paths(file_bytes, actual_format, ocr_temp_dir, f"{announcement_id}_{actual_format}")
                if image_paths:
                    ocr_tasks.append({"announcement_id": announcement_id, "image_paths": image_paths})
                    return "queued-ocr"
        elif actual_format == "hwpx":
            try:
                text = convert_hwpx_bytes_to_text(file_bytes)
            except Exception:
                text = ""
            if not text.strip():
                image_paths = render_office_bytes_to_image_paths(file_bytes, actual_format, ocr_temp_dir, f"{announcement_id}_{actual_format}")
                if image_paths:
                    ocr_tasks.append({"announcement_id": announcement_id, "image_paths": image_paths})
                    return "queued-ocr"
        elif actual_format == "docx":
            try:
                text = convert_docx_bytes_to_text(file_bytes)
            except Exception:
                text = ""
            if not text.strip():
                image_paths = render_office_bytes_to_image_paths(file_bytes, actual_format, ocr_temp_dir, f"{announcement_id}_{actual_format}")
                if image_paths:
                    ocr_tasks.append({"announcement_id": announcement_id, "image_paths": image_paths})
                    return "queued-ocr"
        elif actual_format == "doc":
            temp_file_path = None
            try:
                with tempfile.NamedTemporaryFile(delete=False, suffix=".doc") as temp_file:
                    temp_file.write(file_bytes)
                    temp_file_path = temp_file.name
                text = convert_doc_path_to_text(temp_file_path)
            except Exception:
                text = ""
            finally:
                if temp_file_path:
                    try:
                        os.unlink(temp_file_path)
                    except OSError:
                        pass
            if not text.strip():
                image_paths = render_office_bytes_to_image_paths(file_bytes, actual_format, ocr_temp_dir, f"{announcement_id}_{actual_format}")
                if image_paths:
                    ocr_tasks.append({"announcement_id": announcement_id, "image_paths": image_paths})
                    return "queued-ocr"
        elif is_image_format(actual_format):
            image_path = write_temp_image_bytes(file_bytes, actual_format, ocr_temp_dir, str(announcement_id))
            ocr_tasks.append({"announcement_id": announcement_id, "image_paths": [image_path]})
            return "queued-ocr"
        else:
            text = file_bytes.decode("utf-8", errors="replace")

        if not text.strip():
            return "failed empty_text"
        storage.put_processed_text(announcement_id, text)
        jobs.enqueue("upsert", announcement_id)
        return "ok"
    finally:
        if temp_file_path and os.path.exists(temp_file_path):
            os.remove(temp_file_path)


def main() -> int:
    args = parse_args()
    ensure_database_and_tables()
    storage = MinioStorage()
    storage.ensure_bucket()
    jobs = AnnouncementIndexJobStore()

    ids = [int(item.strip()) for item in args.ids.split(",") if item.strip()] if args.ids.strip() else []
    targets = load_targets(ids, args.limit)
    print(f"[RETRY_UPLOAD] targets={len(targets)}", flush=True)

    ok = skipped = failed = 0
    with tempfile.TemporaryDirectory(prefix="startingblock_retry_ocr_") as ocr_temp_dir, httpx.Client(timeout=args.timeout_seconds, follow_redirects=True) as client:
        ocr_tasks: list[dict[str, Any]] = []
        for index, target in enumerate(targets, start=1):
            announcement_id = int(target["id"])
            try:
                result = process_target(client, storage, jobs, target, ocr_temp_dir, ocr_tasks)
                if result == "ok":
                    ok += 1
                elif result == "queued-ocr":
                    print(f"[RETRY_UPLOAD] {index}/{len(targets)} announcement_id={announcement_id} result=queued-ocr", flush=True)
                    continue
                elif result.startswith("skipped"):
                    skipped += 1
                else:
                    failed += 1
                print(f"[RETRY_UPLOAD] {index}/{len(targets)} announcement_id={announcement_id} result={result}", flush=True)
            except Exception as error:
                failed += 1
                print(f"[RETRY_UPLOAD] {index}/{len(targets)} announcement_id={announcement_id} result=failed error={error}", flush=True)

        if ocr_tasks:
            all_image_paths = [path for task in ocr_tasks for path in task["image_paths"]]
            ocr_texts = convert_image_paths_to_texts(all_image_paths)
            cursor = 0
            for task in ocr_tasks:
                announcement_id = int(task["announcement_id"])
                image_paths = task["image_paths"]
                page_texts = ocr_texts[cursor:cursor + len(image_paths)]
                cursor += len(image_paths)
                text = "\n\n".join(page_text.strip() for page_text in page_texts if page_text and page_text.strip())
                if not text.strip():
                    failed += 1
                    print(f"[RETRY_UPLOAD] announcement_id={announcement_id} result=failed error=ocr empty_text", flush=True)
                    continue
                storage.put_processed_text(announcement_id, text)
                jobs.enqueue("upsert", announcement_id)
                ok += 1
                print(f"[RETRY_UPLOAD] announcement_id={announcement_id} result=ok source=ocr", flush=True)

    print(f"[RETRY_UPLOAD] done ok={ok} skipped={skipped} failed={failed}", flush=True)
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
