#!/bin/sh
set -eu

cleanup() {
  if [ -n "${INDEX_WORKER_PID:-}" ]; then
    kill "${INDEX_WORKER_PID}" 2>/dev/null || true
  fi
  if [ -n "${SPRING_PID:-}" ]; then
    kill "${SPRING_PID}" 2>/dev/null || true
  fi
  if [ -n "${XVFB_PID:-}" ]; then
    kill "${XVFB_PID}" 2>/dev/null || true
  fi
}

trap cleanup INT TERM EXIT

rm -f /tmp/.X99-lock
Xvfb :99 -ac &
XVFB_PID="$!"

cd /app

if [ "${AI_RAG_INDEX_WORKER_ENABLED:-true}" = "true" ]; then
  "${AI_RAG_PYTHON_EXECUTABLE:-/opt/ai-rag-venv/bin/python}" \
    -m app.scripts.index_worker \
    --idle-sleep-seconds "${AI_RAG_INDEX_WORKER_IDLE_SLEEP_SECONDS:-2}" \
    --idle-exit-seconds "${AI_RAG_INDEX_WORKER_IDLE_EXIT_SECONDS:-60}" \
    --stale-processing-seconds "${AI_RAG_INDEX_WORKER_STALE_PROCESSING_SECONDS:-3600}" \
    --cuda-empty-cache-every "${AI_RAG_INDEX_WORKER_CUDA_EMPTY_CACHE_EVERY:-1}" \
    --job-batch-size "${AI_RAG_INDEX_WORKER_JOB_BATCH_SIZE:-20}" \
    --num-gpus "${AI_RAG_INDEX_WORKER_NUM_GPUS:-2}" &
  INDEX_WORKER_PID="$!"
fi

java -jar /app/app.jar &
SPRING_PID="$!"

wait "${SPRING_PID}"
