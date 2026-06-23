#!/bin/sh
set -eu

FALKORDB_DATA_DIR="${FALKORDB_DATA_DIR:-/var/lib/falkordb/data}"
FALKORDB_PORT="${FALKORDB_PORT:-6379}"
FALKORDB_BIND="${FALKORDB_BIND:-0.0.0.0}"
FALKORDB_ARGS="${FALKORDB_ARGS:-THREAD_COUNT 4}"
REDIS_EXTRA_ARGS="${REDIS_ARGS:-}"

mkdir -p "${FALKORDB_DATA_DIR}" /run/nginx

FALKORDB_MODULE_PATH="${FALKORDB_MODULE_PATH:-}"
if [ -z "${FALKORDB_MODULE_PATH}" ]; then
  FALKORDB_MODULE_PATH="$(find / -name falkordb.so -type f 2>/dev/null | head -n 1 || true)"
fi

if [ -z "${FALKORDB_MODULE_PATH}" ]; then
  echo "falkordb.so module not found in image" >&2
  exit 1
fi

redis-server \
  --bind "${FALKORDB_BIND}" \
  --protected-mode no \
  --port "${FALKORDB_PORT}" \
  --appendonly yes \
  --save 60 1000 \
  --dir "${FALKORDB_DATA_DIR}" \
  --loadmodule "${FALKORDB_MODULE_PATH}" ${FALKORDB_ARGS} \
  ${REDIS_EXTRA_ARGS} \
  --daemonize yes

cleanup() {
  redis-cli -p "${FALKORDB_PORT}" shutdown nosave >/dev/null 2>&1 || true
}

trap cleanup INT TERM EXIT

nginx -g "daemon off;"
