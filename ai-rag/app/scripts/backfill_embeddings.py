import argparse
import multiprocessing
import os
from pathlib import Path

from app.api.announcement.vector_indexer import AnnouncementVectorIndexer
from app.core.storage import MinioStorage


def parse_args():
    parser = argparse.ArgumentParser(description="기존 processed 파일을 Qdrant로 백필 임베딩합니다.")
    parser.add_argument("--ids", type=str, default="", help="쉼표 구분 announcement id 목록. 미지정 시 전체 처리")
    parser.add_argument("--limit", type=int, default=0, help="처리 최대 개수(0이면 전체)")
    parser.add_argument(
        "--checkpoint",
        type=str,
        default="app/data/backfill_embeddings.done",
        help="완료 announcement_id를 저장하는 체크포인트 파일 경로",
    )
    parser.add_argument("--skip-existing", dest="skip_existing", action="store_true", default=True)
    parser.add_argument("--no-skip-existing", dest="skip_existing", action="store_false")
    parser.add_argument("--dry-run", action="store_true", help="실제 upsert 없이 대상만 출력")
    parser.add_argument("--num-gpus", type=int, default=0, help="사용할 GPU 수. 0이면 자동 감지")
    return parser.parse_args()


def detect_gpu_count() -> int:
    try:
        import torch
        return max(torch.cuda.device_count(), 0)
    except Exception:
        return 0


def select_target_ids(storage: MinioStorage, id_arg: str, limit: int) -> list[str]:
    if id_arg.strip():
        ids = [item.strip() for item in id_arg.split(",") if item.strip()]
    else:
        ids = storage.list_processed_ids()

    if limit > 0:
        return ids[:limit]
    return ids


def load_checkpoint(path: Path) -> set[str]:
    if not path.exists():
        return set()

    done_ids: set[str] = set()
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        token = line.strip()
        if token:
            done_ids.add(token)
    return done_ids


def append_checkpoint(path: Path, announcement_id: int, lock=None):
    path.parent.mkdir(parents=True, exist_ok=True)
    if lock is not None:
        with lock:
            with path.open("a", encoding="utf-8") as handle:
                handle.write(f"{announcement_id}\n")
    else:
        with path.open("a", encoding="utf-8") as handle:
            handle.write(f"{announcement_id}\n")


def process_one(
    storage: MinioStorage,
    indexer: AnnouncementVectorIndexer,
    raw_id: str,
    dry_run: bool,
    skip_existing: bool,
) -> tuple[str, int | str, int, str]:
    try:
        announcement_id = int(raw_id)
    except ValueError:
        return ("skipped", raw_id, 0, "non-numeric-id")

    text = storage.get_processed_text(announcement_id)
    if text is None:
        return ("skipped", announcement_id, 0, "missing-processed-text")

    if skip_existing and indexer.has_announcement_vectors(announcement_id):
        return ("skipped", announcement_id, 0, "already-indexed")

    if dry_run:
        return ("dry-run", announcement_id, len(text), "")

    try:
        chunk_count = indexer.upsert_announcement(announcement_id, text)
        return ("ok", announcement_id, chunk_count, "")
    except Exception as error:
        return ("fail", announcement_id, 0, str(error))


def _worker(
    gpu_id: int,
    id_chunk: list[str],
    checkpoint_path_str: str,
    dry_run: bool,
    skip_existing: bool,
    lock: multiprocessing.Lock,
    result_queue: multiprocessing.Queue,
):
    os.environ["CUDA_VISIBLE_DEVICES"] = str(gpu_id)

    storage = MinioStorage()
    indexer = AnnouncementVectorIndexer(embedding_device="cuda:0")
    checkpoint_path = Path(checkpoint_path_str)

    done = skipped = failed = 0

    for raw_id in id_chunk:
        status, announcement_id, metric, detail = process_one(
            storage, indexer, raw_id, dry_run, skip_existing
        )

        if status == "ok":
            done += 1
            append_checkpoint(checkpoint_path, int(announcement_id), lock)
            print(f"[GPU{gpu_id}][OK] announcement_id={announcement_id}, chunks={metric}", flush=True)
        elif status == "dry-run":
            done += 1
            print(f"[GPU{gpu_id}][DRY-RUN] announcement_id={announcement_id}, text_len={metric}", flush=True)
        elif status == "skipped":
            skipped += 1
            if detail == "already-indexed" and isinstance(announcement_id, int):
                append_checkpoint(checkpoint_path, announcement_id, lock)
            print(f"[GPU{gpu_id}][SKIP] announcement_id={announcement_id}, reason={detail}", flush=True)
        else:
            failed += 1
            print(f"[GPU{gpu_id}][FAIL] announcement_id={announcement_id}, error={detail}", flush=True)

    result_queue.put({"done": done, "skipped": skipped, "failed": failed})


def main():
    args = parse_args()

    storage = MinioStorage()
    storage.ensure_bucket()

    checkpoint_path = Path(args.checkpoint)
    done_ids = load_checkpoint(checkpoint_path)

    target_ids = select_target_ids(storage, args.ids, args.limit)
    pending_ids = [item for item in target_ids if item not in done_ids]

    total = len(target_ids)
    pending_total = len(pending_ids)
    print(f"total={total}, already_done={len(done_ids)}, pending={pending_total}")

    if pending_total == 0:
        print("--- Summary ---")
        print(f"target={total}, done=0, skipped=0, failed=0")
        return

    num_gpus = args.num_gpus if args.num_gpus > 0 else detect_gpu_count()
    num_gpus = max(1, num_gpus)
    print(f"using {num_gpus} GPU(s)")

    done = skipped = failed = 0

    if num_gpus == 1:
        indexer = AnnouncementVectorIndexer()
        for raw_id in pending_ids:
            status, announcement_id, metric, detail = process_one(
                storage, indexer, raw_id, args.dry_run, args.skip_existing
            )
            if status == "ok":
                done += 1
                append_checkpoint(checkpoint_path, int(announcement_id))
                print(f"[OK] announcement_id={announcement_id}, chunks={metric}")
            elif status == "dry-run":
                done += 1
                print(f"[DRY-RUN] announcement_id={announcement_id}, text_len={metric}")
            elif status == "skipped":
                skipped += 1
                if detail == "already-indexed" and isinstance(announcement_id, int):
                    append_checkpoint(checkpoint_path, announcement_id)
                print(f"[SKIP] announcement_id={announcement_id}, reason={detail}")
            else:
                failed += 1
                print(f"[FAIL] announcement_id={announcement_id}, error={detail}")
    else:
        # GPU 수만큼 ID를 라운드로빈으로 분배
        chunks = [pending_ids[i::num_gpus] for i in range(num_gpus)]
        lock = multiprocessing.Lock()
        result_queue: multiprocessing.Queue = multiprocessing.Queue()

        processes = []
        for gpu_id, chunk in enumerate(chunks):
            p = multiprocessing.Process(
                target=_worker,
                args=(gpu_id, chunk, str(checkpoint_path), args.dry_run, args.skip_existing, lock, result_queue),
            )
            p.start()
            processes.append(p)

        for p in processes:
            p.join()

        while not result_queue.empty():
            r = result_queue.get()
            done += r["done"]
            skipped += r["skipped"]
            failed += r["failed"]

    print("--- Summary ---")
    print(f"target={total}, done={done}, skipped={skipped}, failed={failed}")


if __name__ == "__main__":
    main()
