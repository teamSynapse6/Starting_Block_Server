#!/bin/sh
set -eu

QDRANT_HTTP_PORT="${QDRANT_HTTP_PORT:-6333}"
QDRANT_GRPC_PORT="${QDRANT_GRPC_PORT:-6334}"
QDRANT_STORAGE_PATH="${QDRANT_STORAGE_PATH:-/data/qdrant}"

export QDRANT__SERVICE__HTTP_PORT="${QDRANT_HTTP_PORT}"
export QDRANT__SERVICE__GRPC_PORT="${QDRANT_GRPC_PORT}"
export QDRANT__STORAGE__STORAGE_PATH="${QDRANT_STORAGE_PATH}"

mkdir -p "${QDRANT_STORAGE_PATH}"

cleanup() {
  if [ -n "${SPRING_PID:-}" ]; then
    kill "${SPRING_PID}" 2>/dev/null || true
  fi
  if [ -n "${QDRANT_PID:-}" ]; then
    kill "${QDRANT_PID}" 2>/dev/null || true
  fi
  if [ -n "${XVFB_PID:-}" ]; then
    kill "${XVFB_PID}" 2>/dev/null || true
  fi
}

trap cleanup INT TERM EXIT

rm -f /tmp/.X99-lock
Xvfb :99 -ac &
XVFB_PID="$!"

cd /qdrant
/usr/local/bin/qdrant --config-path /qdrant/config/production.yaml &
QDRANT_PID="$!"

for _ in $(seq 1 60); do
  if curl -fsS "http://127.0.0.1:${QDRANT_HTTP_PORT}/healthz" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

cd /app
java -jar /app/app.jar &
SPRING_PID="$!"

wait "${SPRING_PID}"
