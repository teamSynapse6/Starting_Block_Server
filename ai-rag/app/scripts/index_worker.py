import argparse
import gc
import multiprocessing
import os
import socket
import time

from sqlalchemy import text as sql_text

os.environ.setdefault("PYTORCH_CUDA_ALLOC_CONF", "expandable_segments:True")


def parse_args():
    parser = argparse.ArgumentParser(description="announcement_index_jobs 큐를 소비해 FalkorDB 인덱싱을 수행합니다.")
    parser.add_argument("--once", action="store_true", help="현재 큐만 처리하고 종료합니다.")
    parser.add_argument("--max-jobs", type=int, default=0, help="최대 처리 job 수. 0이면 제한 없음")
    parser.add_argument("--idle-sleep-seconds", type=float, default=2.0, help="큐가 비었을 때 대기 시간")
    parser.add_argument("--idle-exit-seconds", type=float, default=60.0, help="큐가 비어 있으면 지정 시간 후 프로세스를 종료합니다. 0이면 종료하지 않음")
    parser.add_argument("--stale-processing-seconds", type=int, default=3600, help="오래된 processing job을 queued로 되돌릴 기준")
    parser.add_argument("--cuda-empty-cache-every", type=int, default=1, help="N개 job마다 CUDA cache를 비웁니다. 0이면 비활성화")
    parser.add_argument("--job-batch-size", type=int, default=20, help="한 번에 claim해서 배치 임베딩할 job 수")
    parser.add_argument("--num-gpus", type=int, default=1, help="GPU worker 프로세스 수")
    parser.add_argument("--worker-device", type=str, default="", help=argparse.SUPPRESS)
    return parser.parse_args()


def release_memory(report: bool = False):
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
                print(f"[INDEX_WORKER][CUDA] cache_cleared allocated_mb={allocated:.0f} reserved_mb={reserved:.0f}", flush=True)
    except Exception as error:
        if report:
            print(f"[INDEX_WORKER][CUDA] cache_clear_skipped error={error}", flush=True)


def mark_file_uploaded(announcement_id: int, uploaded: bool):
    from app.core.db_models import get_db_session

    with get_db_session() as db:
        db.execute(
            sql_text("UPDATE announcement SET is_file_uploaded = :uploaded WHERE id = :id"),
            {"id": announcement_id, "uploaded": uploaded},
        )
        db.commit()


def format_ids(ids: list[int], max_items: int = 16) -> str:
    visible = [str(item) for item in ids[:max_items]]
    suffix = "" if len(ids) <= max_items else f", ... +{len(ids) - max_items}"
    return "[" + ", ".join(visible) + suffix + "]"


def mark_jobs_failed(store, jobs: list[dict], error: Exception):
    for job in jobs:
        store.mark_failed(int(job["id"]), str(error))


def process_upsert_batch(indexer, storage, store, jobs: list[dict]) -> int:
    prepared: list[tuple[dict, str]] = []
    processed = 0

    for job in jobs:
        job_id = int(job["id"])
        announcement_id = int(job["announcement_id"])
        try:
            text = storage.get_processed_text(announcement_id)
            if text is None:
                raise RuntimeError("processed text not found")
            mark_file_uploaded(announcement_id, False)
            if not text.strip():
                store.mark_done(job_id)
                print(
                    f"[INDEX_WORKER][JOB] done job_id={job_id} action=upsert "
                    f"announcement_id={announcement_id} chunks=0 empty_text",
                    flush=True,
                )
                processed += 1
                continue
            prepared.append((job, text))
        except Exception as error:
            store.mark_failed(job_id, str(error))
            print(
                f"[INDEX_WORKER][JOB] failed job_id={job_id} action=upsert "
                f"announcement_id={announcement_id} error={error}",
                flush=True,
            )
            processed += 1

    if not prepared:
        return processed

    items = [(int(job["announcement_id"]), text) for job, text in prepared]
    try:
        chunk_counts = indexer.upsert_announcements(items)
    except Exception as batch_error:
        print(f"[INDEX_WORKER][BATCH] batch_failed fallback_to_single error={batch_error}", flush=True)
        for job, text in prepared:
            job_id = int(job["id"])
            announcement_id = int(job["announcement_id"])
            started_at = time.perf_counter()
            try:
                chunk_count = indexer.upsert_announcement(announcement_id, text)
                if chunk_count <= 0:
                    raise RuntimeError("embedding produced no chunks")
                mark_file_uploaded(announcement_id, True)
                store.mark_done(job_id)
                elapsed = time.perf_counter() - started_at
                print(
                    f"[INDEX_WORKER][JOB] done job_id={job_id} action=upsert "
                    f"announcement_id={announcement_id} chunks={chunk_count} elapsed_seconds={elapsed:.1f}",
                    flush=True,
                )
            except Exception as error:
                store.mark_failed(job_id, str(error))
                elapsed = time.perf_counter() - started_at
                print(
                    f"[INDEX_WORKER][JOB] failed job_id={job_id} action=upsert "
                    f"announcement_id={announcement_id} error={error} elapsed_seconds={elapsed:.1f}",
                    flush=True,
                )
            processed += 1
        return processed

    for job, _text in prepared:
        job_id = int(job["id"])
        announcement_id = int(job["announcement_id"])
        chunk_count = int(chunk_counts.get(announcement_id, 0))
        if chunk_count <= 0:
            store.mark_failed(job_id, "embedding produced no chunks")
            print(
                f"[INDEX_WORKER][JOB] failed job_id={job_id} action=upsert "
                f"announcement_id={announcement_id} error=embedding produced no chunks",
                flush=True,
            )
        else:
            mark_file_uploaded(announcement_id, True)
            store.mark_done(job_id)
            print(
                f"[INDEX_WORKER][JOB] done job_id={job_id} action=upsert "
                f"announcement_id={announcement_id} chunks={chunk_count}",
                flush=True,
            )
        processed += 1

    return processed


def run_worker(args, embedding_device: str, requeue_stale: bool = True) -> int:

    from app.api.announcement.index_job_store import AnnouncementIndexJobStore
    from app.core.config import EMBEDDING_DEVICE
    from app.core.storage import MinioStorage

    storage = MinioStorage()
    storage.ensure_bucket()
    store = AnnouncementIndexJobStore()

    requeued = store.requeue_stale_processing(args.stale_processing_seconds) if requeue_stale else 0
    if requeued:
        print(f"[INDEX_WORKER] requeued_stale_processing count={requeued}", flush=True)

    worker_id = f"{socket.gethostname()}:{os.getpid()}"
    device = embedding_device or EMBEDDING_DEVICE
    print(f"[INDEX_WORKER] starting worker_id={worker_id} device={device} batch_size={args.job_batch_size}", flush=True)
    indexer = None

    def ensure_indexer():
        nonlocal indexer
        if indexer is None:
            from app.api.announcement.falkor_indexer import AnnouncementFalkorIndexer

            indexer = AnnouncementFalkorIndexer(device)
            indexer.ensure_ready()
            print("[INDEX_WORKER] indexer ready", flush=True)
        return indexer

    processed = 0
    idle_started_at = time.monotonic()
    while True:
        if args.max_jobs > 0 and processed >= args.max_jobs:
            break

        remaining = args.max_jobs - processed if args.max_jobs > 0 else args.job_batch_size
        claim_limit = max(1, min(args.job_batch_size, remaining))
        jobs = store.claim_batch(worker_id, claim_limit)
        if not jobs:
            if args.once:
                break
            if args.idle_exit_seconds > 0 and time.monotonic() - idle_started_at >= args.idle_exit_seconds:
                print(f"[INDEX_WORKER] idle_exit idle_seconds={args.idle_exit_seconds}", flush=True)
                break
            time.sleep(max(args.idle_sleep_seconds, 0.1))
            continue
        idle_started_at = time.monotonic()

        started_at = time.perf_counter()
        announcement_ids = [int(job["announcement_id"]) for job in jobs]
        print(
            f"[INDEX_WORKER][BATCH] start size={len(jobs)} announcement_ids={format_ids(announcement_ids)}",
            flush=True,
        )

        try:
            upsert_jobs = [job for job in jobs if str(job["action"]) == "upsert"]
            delete_jobs = [job for job in jobs if str(job["action"]) == "delete"]
            unsupported_jobs = [job for job in jobs if str(job["action"]) not in {"upsert", "delete"}]

            for job in unsupported_jobs:
                store.mark_failed(int(job["id"]), f"unsupported action: {job['action']}")

            if delete_jobs:
                delete_ids = [int(job["announcement_id"]) for job in delete_jobs]
                ensure_indexer().delete_announcements(delete_ids)
                for job in delete_jobs:
                    announcement_id = int(job["announcement_id"])
                    mark_file_uploaded(announcement_id, False)
                    store.mark_done(int(job["id"]))
                    print(
                        f"[INDEX_WORKER][JOB] done job_id={job['id']} action=delete "
                        f"announcement_id={announcement_id} deleted",
                        flush=True,
                    )

            batch_processed = len(unsupported_jobs) + len(delete_jobs)
            if upsert_jobs:
                batch_processed += process_upsert_batch(ensure_indexer(), storage, store, upsert_jobs)
            processed += batch_processed

            elapsed = time.perf_counter() - started_at
            print(
                f"[INDEX_WORKER][BATCH] done size={len(jobs)} processed={batch_processed} "
                f"elapsed_seconds={elapsed:.1f}",
                flush=True,
            )
        except Exception as error:
            mark_jobs_failed(store, jobs, error)
            elapsed = time.perf_counter() - started_at
            print(
                f"[INDEX_WORKER][BATCH] failed size={len(jobs)} "
                f"announcement_ids={format_ids(announcement_ids)} error={error} elapsed_seconds={elapsed:.1f}",
                flush=True,
            )
            processed += len(jobs)
        if args.cuda_empty_cache_every and processed % args.cuda_empty_cache_every == 0:
            release_memory(report=True)

    print(f"[INDEX_WORKER] stopped processed={processed}", flush=True)
    return 0


def detect_gpu_count() -> int:
    try:
        import torch

        return max(torch.cuda.device_count(), 0)
    except Exception:
        return 0


def gpu_worker(args, gpu_id: int):
    os.environ["CUDA_VISIBLE_DEVICES"] = str(gpu_id)
    print(f"[INDEX_WORKER][GPU{gpu_id}] visible_device=cuda:0", flush=True)
    raise SystemExit(run_worker(args, "cuda:0", requeue_stale=False))


def main() -> int:
    args = parse_args()
    if args.worker_device:
        return run_worker(args, args.worker_device)

    num_gpus = max(int(args.num_gpus), 1)
    available_gpus = detect_gpu_count()
    if num_gpus > 1 and available_gpus > 0:
        from app.api.announcement.index_job_store import AnnouncementIndexJobStore

        requeued = AnnouncementIndexJobStore().requeue_stale_processing(args.stale_processing_seconds)
        if requeued:
            print(f"[INDEX_WORKER][MAIN] requeued_stale_processing count={requeued}", flush=True)

        num_gpus = min(num_gpus, available_gpus)
        context = multiprocessing.get_context("spawn")
        processes: list[multiprocessing.Process] = []
        for gpu_id in range(num_gpus):
            print(f"[INDEX_WORKER][MAIN] launching GPU{gpu_id} worker", flush=True)
            process = context.Process(target=gpu_worker, args=(args, gpu_id))
            process.start()
            processes.append(process)

        exit_code = 0
        for process in processes:
            process.join()
            if process.exitcode:
                exit_code = process.exitcode
        return exit_code

    return run_worker(args, "")


if __name__ == "__main__":
    raise SystemExit(main())
