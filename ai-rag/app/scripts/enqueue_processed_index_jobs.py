import argparse


def parse_args():
    parser = argparse.ArgumentParser(description="MinIO processed 파일을 기준으로 FalkorDB upsert job을 큐에 넣습니다.")
    parser.add_argument("--ids", type=str, default="", help="쉼표 구분 announcement id 목록. 미지정 시 전체 processed 대상")
    parser.add_argument("--limit", type=int, default=0, help="큐에 넣을 최대 개수. 0이면 제한 없음")
    parser.add_argument("--requeue-failed", action="store_true", help="failed 상태의 기존 job도 queued로 되돌립니다.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    from app.api.announcement.index_job_store import AnnouncementIndexJobStore
    from app.core.db_models import ensure_database_and_tables
    from app.core.storage import MinioStorage

    ensure_database_and_tables()
    storage = MinioStorage()
    storage.ensure_bucket()
    store = AnnouncementIndexJobStore()

    if args.ids.strip():
        ids = [item.strip() for item in args.ids.split(",") if item.strip()]
    else:
        ids = storage.list_processed_ids()

    if args.limit > 0:
        ids = ids[:args.limit]

    numeric_ids: list[int] = []
    skipped = 0
    for raw_id in ids:
        try:
            numeric_ids.append(int(raw_id))
        except ValueError:
            skipped += 1

    requeued_failed = store.requeue_failed(numeric_ids) if args.requeue_failed else 0

    enqueued = 0
    failed = 0
    for announcement_id in numeric_ids:
        try:
            store.enqueue("upsert", announcement_id)
            enqueued += 1
        except Exception as error:
            failed += 1
            print(f"[ENQUEUE][FAIL] announcement_id={announcement_id} error={error}", flush=True)

    print(
        f"[ENQUEUE] target={len(ids)} numeric={len(numeric_ids)} "
        f"enqueued={enqueued} skipped={skipped} failed={failed} requeued_failed={requeued_failed}",
        flush=True,
    )
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
