import argparse
import gc
import multiprocessing
import os
import queue
import threading
import time
from pathlib import Path

from app.core.storage import MinioStorage

os.environ.setdefault("PYTORCH_CUDA_ALLOC_CONF", "expandable_segments:True")


def parse_args():
    parser = argparse.ArgumentParser(description="기존 processed 파일을 FalkorDB로 백필 임베딩합니다.")
    parser.add_argument("--ids", type=str, default="", help="쉼표 구분 announcement id 목록. 미지정 시 전체 처리")
    parser.add_argument("--limit", type=int, default=0, help="처리 최대 개수(0이면 전체)")
    parser.add_argument(
        "--checkpoint",
        type=str,
        default="app/data/backfill_falkordb.done",
        help="완료 announcement_id를 저장하는 체크포인트 파일 경로",
    )
    parser.add_argument("--skip-existing", dest="skip_existing", action="store_true", default=False)
    parser.add_argument("--no-skip-existing", dest="skip_existing", action="store_false")
    parser.add_argument("--dry-run", action="store_true", help="실제 upsert 없이 대상만 출력")
    parser.add_argument("--num-gpus", type=int, default=0, help="사용할 GPU 수. 0이면 자동 감지")
    parser.add_argument("--announcement-batch-size", type=int, default=16, help="GPU worker가 한 번에 임베딩할 announcement 개수")
    parser.add_argument("--prefetch-batches", type=int, default=3, help="GPU worker별로 미리 로드해둘 batch 개수")
    parser.add_argument("--cuda-empty-cache-every", type=int, default=1, help="N개 batch마다 CUDA cache를 비웁니다. 0이면 비활성화")
    return parser.parse_args()


def detect_gpu_count() -> int:
    try:
        import torch
        return max(torch.cuda.device_count(), 0)
    except Exception:
        return 0


def release_batch_memory(gpu_id: int | None = None, report: bool = False) -> None:
    gc.collect()
    try:
        import torch

        if torch.cuda.is_available():
            torch.cuda.synchronize()
            torch.cuda.empty_cache()
            torch.cuda.ipc_collect()
            if report:
                allocated = torch.cuda.memory_allocated() / (1024 ** 2)
                reserved = torch.cuda.memory_reserved() / (1024 ** 2)
                prefix = f"[GPU{gpu_id}]" if gpu_id is not None else "[GPU]"
                print(
                    f"{prefix}[CUDA] cache_cleared allocated_mb={allocated:.0f} reserved_mb={reserved:.0f}",
                    flush=True,
                )
    except Exception as error:
        if report:
            prefix = f"[GPU{gpu_id}]" if gpu_id is not None else "[GPU]"
            print(f"{prefix}[CUDA] cache_clear_skipped error={error}", flush=True)


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


def format_ids_for_log(ids: list[int | str], max_items: int = 12) -> str:
    if not ids:
        return "[]"

    visible = [str(item) for item in ids[:max_items]]
    suffix = "" if len(ids) <= max_items else f", ... +{len(ids) - max_items}"
    return "[" + ", ".join(visible) + suffix + "]"


def process_one(
    storage: MinioStorage,
    indexer,
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


def process_batch(
    storage: MinioStorage,
    indexer,
    raw_ids: list[str],
    dry_run: bool,
    skip_existing: bool,
) -> list[tuple[str, int | str, int, str]]:
    results: list[tuple[str, int | str, int, str]] = []
    upsert_items: list[tuple[int, str]] = []

    for raw_id in raw_ids:
        try:
            announcement_id = int(raw_id)
        except ValueError:
            results.append(("skipped", raw_id, 0, "non-numeric-id"))
            continue

        text = storage.get_processed_text(announcement_id)
        if text is None:
            results.append(("skipped", announcement_id, 0, "missing-processed-text"))
            continue

        if skip_existing and indexer.has_announcement_vectors(announcement_id):
            results.append(("skipped", announcement_id, 0, "already-indexed"))
            continue

        if dry_run:
            results.append(("dry-run", announcement_id, len(text), ""))
            continue

        upsert_items.append((announcement_id, text))

    if not upsert_items:
        return results

    try:
        chunk_counts = indexer.upsert_announcements(upsert_items)
        for announcement_id, _text in upsert_items:
            results.append(("ok", announcement_id, chunk_counts.get(announcement_id, 0), ""))
    except Exception as error:
        for announcement_id, _text in upsert_items:
            results.append(("fail", announcement_id, 0, str(error)))

    return results


def prepare_batch(
    storage: MinioStorage,
    raw_ids: list[str],
    dry_run: bool,
) -> tuple[list[tuple[str, int | str, int, str]], list[tuple[int, str]]]:
    results: list[tuple[str, int | str, int, str]] = []
    upsert_items: list[tuple[int, str]] = []

    for raw_id in raw_ids:
        try:
            announcement_id = int(raw_id)
        except ValueError:
            results.append(("skipped", raw_id, 0, "non-numeric-id"))
            continue

        text = storage.get_processed_text(announcement_id)
        if text is None:
            results.append(("skipped", announcement_id, 0, "missing-processed-text"))
            continue

        if dry_run:
            results.append(("dry-run", announcement_id, len(text), ""))
            continue

        upsert_items.append((announcement_id, text))

    return results, upsert_items


def process_prepared_batch(
    indexer,
    prepared_results: list[tuple[str, int | str, int, str]],
    upsert_items: list[tuple[int, str]],
    skip_existing: bool,
    gpu_id: int | None = None,
) -> list[tuple[str, int | str, int, str]]:
    results = list(prepared_results)
    if not upsert_items:
        return results

    if skip_existing:
        filtered_items: list[tuple[int, str]] = []
        for announcement_id, text in upsert_items:
            if indexer.has_announcement_vectors(announcement_id):
                results.append(("skipped", announcement_id, 0, "already-indexed"))
            else:
                filtered_items.append((announcement_id, text))
        upsert_items = filtered_items

    if not upsert_items:
        return results

    try:
        prefix = f"[GPU{gpu_id}]" if gpu_id is not None else "[GPU]"
        print(
            f"{prefix}[UPSERT] processing announcement_ids="
            f"{format_ids_for_log([announcement_id for announcement_id, _text in upsert_items])}",
            flush=True,
        )
        chunk_counts = indexer.upsert_announcements(upsert_items)
        for announcement_id, _text in upsert_items:
            results.append(("ok", announcement_id, chunk_counts.get(announcement_id, 0), ""))
    except Exception as error:
        for announcement_id, _text in upsert_items:
            results.append(("fail", announcement_id, 0, str(error)))

    return results


def _prefetch_batches(
    gpu_id: int,
    storage: MinioStorage,
    id_chunk: list[str],
    announcement_batch_size: int,
    dry_run: bool,
    output_queue: queue.Queue,
):
    for start in range(0, len(id_chunk), announcement_batch_size):
        raw_ids = id_chunk[start:start + announcement_batch_size]
        started_at = time.perf_counter()
        prepared_results, upsert_items = prepare_batch(storage, raw_ids, dry_run)
        elapsed = time.perf_counter() - started_at
        output_queue.put({
            "type": "batch",
            "start": start,
            "raw_ids": raw_ids,
            "prepared_results": prepared_results,
            "upsert_items": upsert_items,
            "load_seconds": elapsed,
        })
        upsert_ids = [announcement_id for announcement_id, _text in upsert_items]
        print(
            f"[GPU{gpu_id}][PREFETCH] loaded offset={start} size={len(raw_ids)} "
            f"upsert={len(upsert_items)} announcement_ids={format_ids_for_log(upsert_ids)} "
            f"elapsed_seconds={elapsed:.1f}",
            flush=True,
        )

    output_queue.put({"type": "done"})


def _worker(
    gpu_id: int,
    id_chunk: list[str],
    checkpoint_path_str: str,
    dry_run: bool,
    skip_existing: bool,
    announcement_batch_size: int,
    prefetch_batches: int,
    cuda_empty_cache_every: int,
    lock: multiprocessing.Lock,
    result_queue: multiprocessing.Queue,
):
    os.environ["CUDA_VISIBLE_DEVICES"] = str(gpu_id)

    print(f"[GPU{gpu_id}][INIT] worker starting targets={len(id_chunk)}", flush=True)
    storage = MinioStorage()
    try:
        from app.api.announcement.falkor_indexer import AnnouncementFalkorIndexer

        started_at = time.perf_counter()
        indexer = AnnouncementFalkorIndexer(embedding_device="cuda:0")
        indexer.ensure_ready()
        elapsed = time.perf_counter() - started_at
        print(f"[GPU{gpu_id}][INIT] indexer ready elapsed_seconds={elapsed:.1f}", flush=True)
    except Exception as error:
        result_queue.put({"done": 0, "skipped": 0, "failed": len(id_chunk)})
        print(f"[GPU{gpu_id}][FAIL] indexer initialization failed: {error}", flush=True)
        return
    checkpoint_path = Path(checkpoint_path_str)

    done = skipped = failed = 0

    announcement_batch_size = max(1, announcement_batch_size)
    prefetch_batches = max(1, prefetch_batches)
    cuda_empty_cache_every = max(0, cuda_empty_cache_every)
    prefetch_queue: queue.Queue = queue.Queue(maxsize=prefetch_batches)
    prefetch_thread = threading.Thread(
        target=_prefetch_batches,
        args=(gpu_id, storage, id_chunk, announcement_batch_size, dry_run, prefetch_queue),
        daemon=True,
    )
    prefetch_thread.start()

    processed_batches = 0
    while True:
        prepared = prefetch_queue.get()
        if prepared.get("type") == "done":
            break

        start = prepared["start"]
        batch = prepared["raw_ids"]
        batch_started_at = time.perf_counter()
        upsert_ids = [announcement_id for announcement_id, _text in prepared["upsert_items"]]
        print(
            f"[GPU{gpu_id}][BATCH] start offset={start} size={len(batch)} "
            f"prefetched={len(prepared['upsert_items'])} "
            f"announcement_ids={format_ids_for_log(upsert_ids)} "
            f"load_seconds={prepared['load_seconds']:.1f}",
            flush=True,
        )
        batch_results = process_prepared_batch(
            indexer,
            prepared["prepared_results"],
            prepared["upsert_items"],
            skip_existing,
            gpu_id,
        )
        elapsed = time.perf_counter() - batch_started_at

        batch_done = batch_skipped = batch_failed = batch_chunks = 0
        batch_ok_ids: list[int | str] = []
        batch_skipped_ids: list[int | str] = []
        batch_failed_ids: list[int | str] = []
        for status, announcement_id, metric, detail in batch_results:
            if status == "ok":
                done += 1
                batch_done += 1
                batch_chunks += metric
                batch_ok_ids.append(announcement_id)
                append_checkpoint(checkpoint_path, int(announcement_id), lock)
            elif status == "dry-run":
                done += 1
                batch_done += 1
                batch_ok_ids.append(announcement_id)
            elif status == "skipped":
                skipped += 1
                batch_skipped += 1
                batch_skipped_ids.append(announcement_id)
                if detail == "already-indexed" and isinstance(announcement_id, int):
                    append_checkpoint(checkpoint_path, announcement_id, lock)
            else:
                failed += 1
                batch_failed += 1
                batch_failed_ids.append(announcement_id)
                print(f"[GPU{gpu_id}][FAIL] announcement_id={announcement_id}, error={detail}", flush=True)

        print(
            f"[GPU{gpu_id}][BATCH] done offset={start} size={len(batch)} "
            f"ok={batch_done} skipped={batch_skipped} failed={batch_failed} chunks={batch_chunks} "
            f"ok_ids={format_ids_for_log(batch_ok_ids)} "
            f"skipped_ids={format_ids_for_log(batch_skipped_ids)} "
            f"failed_ids={format_ids_for_log(batch_failed_ids)} "
            f"elapsed_seconds={elapsed:.1f}",
            flush=True,
        )
        del batch_results
        del prepared
        processed_batches += 1
        if cuda_empty_cache_every and processed_batches % cuda_empty_cache_every == 0:
            release_batch_memory(gpu_id, report=True)

    prefetch_thread.join()
    release_batch_memory(gpu_id, report=False)
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
    num_gpus = min(num_gpus, pending_total)
    print(f"using {num_gpus} GPU(s)")

    done = skipped = failed = 0

    if num_gpus == 1:
        from app.api.announcement.falkor_indexer import AnnouncementFalkorIndexer

        print("[INIT] single worker indexer starting", flush=True)
        indexer = AnnouncementFalkorIndexer()
        indexer.ensure_ready()
        print("[INIT] single worker indexer ready", flush=True)
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
            if args.cuda_empty_cache_every and (done + skipped + failed) % args.cuda_empty_cache_every == 0:
                release_batch_memory(report=True)
    else:
        # GPU 수만큼 ID를 라운드로빈으로 분배
        chunks = [pending_ids[i::num_gpus] for i in range(num_gpus)]
        lock = multiprocessing.Lock()
        result_queue: multiprocessing.Queue = multiprocessing.Queue()

        processes = []
        for gpu_id, chunk in enumerate(chunks):
            if not chunk:
                continue
            print(f"[MAIN] launching GPU{gpu_id} worker targets={len(chunk)}", flush=True)
            p = multiprocessing.Process(
                target=_worker,
                args=(
                    gpu_id,
                    chunk,
                    str(checkpoint_path),
                    args.dry_run,
                    args.skip_existing,
                    args.announcement_batch_size,
                    args.prefetch_batches,
                    args.cuda_empty_cache_every,
                    lock,
                    result_queue,
                ),
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
