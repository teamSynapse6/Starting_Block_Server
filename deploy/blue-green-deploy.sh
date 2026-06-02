#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NGINX_CONF="${PROJECT_DIR}/deploy/nginx/nginx.conf"
PROXY_CONTAINER="${PROXY_CONTAINER:-startingblock-proxy}"
RUN_BACKFILL="${RUN_BACKFILL:-false}"
STOP_OLD="${STOP_OLD:-false}"

cd "${PROJECT_DIR}"

ACTIVE="$(grep -Eo 'server spring-(blue|green):8080;' "${NGINX_CONF}" | head -1 | sed -E 's/.*spring-(blue|green).*/\1/' || true)"
if [[ "${ACTIVE}" == "blue" ]]; then
  TARGET="green"
  OLD="blue"
  COMPOSE_PROFILE_ARGS=(--profile green)
elif [[ "${ACTIVE}" == "green" ]]; then
  TARGET="blue"
  OLD="green"
  COMPOSE_PROFILE_ARGS=()
else
  TARGET="blue"
  OLD="green"
  COMPOSE_PROFILE_ARGS=()
fi

echo "Active color: ${ACTIVE:-none}"
echo "Deploy target: ${TARGET}"

docker compose "${COMPOSE_PROFILE_ARGS[@]}" up -d --build "spring-${TARGET}"

echo "Waiting for spring-${TARGET} health..."
for _ in $(seq 1 90); do
  if docker compose "${COMPOSE_PROFILE_ARGS[@]}" exec -T "spring-${TARGET}" curl -fsS "http://127.0.0.1:8080/health" >/dev/null 2>&1; then
    break
  fi
  STATUS="$(docker inspect -f '{{.State.Status}}' "startingblock-spring-${TARGET}" 2>/dev/null || true)"
  if [[ "${STATUS}" == "exited" || "${STATUS}" == "dead" ]]; then
    echo "spring-${TARGET} stopped before becoming healthy."
    docker compose "${COMPOSE_PROFILE_ARGS[@]}" logs --tail=120 "spring-${TARGET}"
    exit 1
  fi
  sleep 2
done

if ! docker compose "${COMPOSE_PROFILE_ARGS[@]}" exec -T "spring-${TARGET}" curl -fsS "http://127.0.0.1:8080/health" >/dev/null 2>&1; then
  echo "spring-${TARGET} did not become healthy in time."
  docker compose "${COMPOSE_PROFILE_ARGS[@]}" logs --tail=120 "spring-${TARGET}"
  exit 1
fi

if [[ "${RUN_BACKFILL}" == "true" ]]; then
  docker compose "${COMPOSE_PROFILE_ARGS[@]}" exec -T "spring-${TARGET}" \
    /opt/ai-rag-venv/bin/python -m app.scripts.migrate_processed_to_minio
  docker compose "${COMPOSE_PROFILE_ARGS[@]}" exec -T "spring-${TARGET}" \
    /opt/ai-rag-venv/bin/python -m app.scripts.backfill_embeddings
fi

sed -i -E "s/server spring-(blue|green):8080;/server spring-${TARGET}:8080;/" "${NGINX_CONF}"

PROXY_STATUS="$(docker inspect -f '{{.State.Status}}' "${PROXY_CONTAINER}" 2>/dev/null || true)"
if [[ "${PROXY_STATUS}" == "running" ]]; then
  docker exec "${PROXY_CONTAINER}" nginx -s reload
else
  docker compose up -d --build --force-recreate proxy
fi

echo "Traffic switched: spring-${OLD} -> spring-${TARGET}"

if [[ "${STOP_OLD}" == "true" ]]; then
  if [[ "${OLD}" == "green" ]]; then
    docker compose --profile green stop "spring-${OLD}" || true
  else
    docker compose stop "spring-${OLD}" || true
  fi
fi
