import argparse
from html.parser import HTMLParser
from typing import Any
from urllib.parse import urljoin

import httpx
from sqlalchemy import text as sql_text

from app.api.announcement.index_job_store import AnnouncementIndexJobStore
from app.core.db_models import ensure_database_and_tables, get_db_session
from app.core.storage import MinioStorage
from app.scripts.retry_unuploaded_files import process_target


K_STARTUP_BASE_URL = "https://www.k-startup.go.kr"
DEFAULT_CREATED_FROM = "2026-06-01 00:00:00"


class KStartupAttachmentParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self._board_file_depth = 0
        self.first_download_href: str | None = None

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]):
        attr_map = {name.lower(): value or "" for name, value in attrs}
        if tag.lower() == "div":
            classes = set(attr_map.get("class", "").split())
            if "board_file" in classes:
                self._board_file_depth = 1
                return
            if self._board_file_depth > 0:
                self._board_file_depth += 1

        if self._board_file_depth <= 0 or self.first_download_href is not None or tag.lower() != "a":
            return

        classes = set(attr_map.get("class", "").split())
        if attr_map.get("name") == "downloadBtn" and "btn_down" in classes:
            href = attr_map.get("href", "").strip()
            if href and not href.lower().startswith("javascript:"):
                self.first_download_href = href

    def handle_endtag(self, tag: str):
        if tag.lower() == "div" and self._board_file_depth > 0:
            self._board_file_depth -= 1


def parse_args():
    parser = argparse.ArgumentParser(
        description="2026년 6월 이후 저장된 K-Startup 공고의 첫 첨부파일 URL을 백필하고 선택적으로 전처리/인덱싱합니다."
    )
    parser.add_argument("--created-from", default=DEFAULT_CREATED_FROM, help="created_at 하한. 기본: 2026-06-01 00:00:00")
    parser.add_argument("--limit", type=int, default=0, help="처리 최대 개수. 0이면 제한 없음")
    parser.add_argument("--upload", action="store_true", help="URL 백필 성공 공고를 바로 다운로드/전처리/인덱싱합니다.")
    parser.add_argument("--timeout-seconds", type=float, default=10.0, help="K-Startup 상세 HTML 조회 timeout")
    parser.add_argument("--download-timeout-seconds", type=float, default=60.0, help="첨부파일 다운로드 timeout")
    return parser.parse_args()


def load_targets(created_from: str, limit: int) -> list[dict[str, Any]]:
    query = """
        SELECT id, detail_url
        FROM announcement
        WHERE announcement_type = 'OPEN_DATA'
          AND detail_url IS NOT NULL
          AND detail_url <> ''
          AND (file_url IS NULL OR file_url = '')
          AND created_at >= :created_from
        ORDER BY id ASC
    """
    if limit > 0:
        query += " LIMIT :limit"

    params: dict[str, Any] = {"created_from": created_from}
    if limit > 0:
        params["limit"] = limit

    with get_db_session() as db:
        rows = db.execute(sql_text(query), params).mappings().all()
    return [{"id": int(row["id"]), "detail_url": str(row["detail_url"])} for row in rows]


def extract_first_download_url(client: httpx.Client, detail_url: str) -> str | None:
    response = client.get(detail_url)
    response.raise_for_status()
    parser = KStartupAttachmentParser()
    parser.feed(response.text)
    if not parser.first_download_href:
        return None
    return urljoin(K_STARTUP_BASE_URL, parser.first_download_href)


def update_file_url(announcement_id: int, file_url: str):
    with get_db_session() as db:
        db.execute(
            sql_text(
                """
                UPDATE announcement
                SET file_url = :file_url,
                    is_file_uploaded = false
                WHERE id = :announcement_id
                """
            ),
            {"file_url": file_url, "announcement_id": announcement_id},
        )
        db.commit()


def main() -> int:
    args = parse_args()
    ensure_database_and_tables()

    targets = load_targets(args.created_from, args.limit)
    print(f"[KSTARTUP_BACKFILL] targets={len(targets)} created_from={args.created_from}", flush=True)

    updated_targets: list[dict[str, Any]] = []
    missing = failed = 0
    with httpx.Client(timeout=args.timeout_seconds, follow_redirects=True) as client:
        for index, target in enumerate(targets, start=1):
            announcement_id = int(target["id"])
            detail_url = str(target["detail_url"])
            try:
                file_url = extract_first_download_url(client, detail_url)
                if not file_url:
                    missing += 1
                    print(f"[KSTARTUP_BACKFILL] {index}/{len(targets)} announcement_id={announcement_id} result=no-file", flush=True)
                    continue
                update_file_url(announcement_id, file_url)
                updated_targets.append({"id": announcement_id, "url": file_url})
                print(f"[KSTARTUP_BACKFILL] {index}/{len(targets)} announcement_id={announcement_id} result=updated url={file_url}", flush=True)
            except Exception as error:
                failed += 1
                print(f"[KSTARTUP_BACKFILL] {index}/{len(targets)} announcement_id={announcement_id} result=failed error={error}", flush=True)

    upload_ok = upload_failed = 0
    if args.upload and updated_targets:
        storage = MinioStorage()
        storage.ensure_bucket()
        jobs = AnnouncementIndexJobStore()
        import tempfile

        with tempfile.TemporaryDirectory(prefix="startingblock_kstartup_ocr_") as ocr_temp_dir, httpx.Client(
            timeout=args.download_timeout_seconds, follow_redirects=True
        ) as client:
            ocr_tasks: list[dict[str, Any]] = []
            for index, target in enumerate(updated_targets, start=1):
                announcement_id = int(target["id"])
                try:
                    result = process_target(client, storage, jobs, target, ocr_temp_dir, ocr_tasks)
                    if result == "ok":
                        upload_ok += 1
                    elif result == "queued-ocr":
                        pass
                    else:
                        upload_failed += 1
                    print(f"[KSTARTUP_BACKFILL][UPLOAD] {index}/{len(updated_targets)} announcement_id={announcement_id} result={result}", flush=True)
                except Exception as error:
                    upload_failed += 1
                    print(f"[KSTARTUP_BACKFILL][UPLOAD] {index}/{len(updated_targets)} announcement_id={announcement_id} result=failed error={error}", flush=True)

            if ocr_tasks:
                from app.api.announcement.file_pipeline import convert_image_paths_to_texts

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
                        upload_failed += 1
                        print(f"[KSTARTUP_BACKFILL][UPLOAD] announcement_id={announcement_id} result=failed error=ocr empty_text", flush=True)
                        continue
                    storage.put_processed_text(announcement_id, text)
                    jobs.enqueue("upsert", announcement_id)
                    upload_ok += 1
                    print(f"[KSTARTUP_BACKFILL][UPLOAD] announcement_id={announcement_id} result=ok source=ocr", flush=True)

    print(
        f"[KSTARTUP_BACKFILL] done updated={len(updated_targets)} missing={missing} failed={failed} "
        f"upload_ok={upload_ok} upload_failed={upload_failed}",
        flush=True,
    )
    return 0 if failed == 0 and upload_failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
