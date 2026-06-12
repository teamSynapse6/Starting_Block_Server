#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NGINX_CONF="${PROJECT_DIR}/deploy/nginx/nginx.conf"
PROXY_CONTAINER="${PROXY_CONTAINER:-startingblock-proxy}"
RUN_BACKFILL="${RUN_BACKFILL:-false}"
STOP_OLD="${STOP_OLD:-true}"
PRUNE_OLD_BUILD_CACHE="${PRUNE_OLD_BUILD_CACHE:-true}"
SPRING_IMAGE="${SPRING_IMAGE:-startingblock-spring:latest}"
export DOCKER_BUILDKIT="${DOCKER_BUILDKIT:-1}"
export COMPOSE_DOCKER_CLI_BUILD="${COMPOSE_DOCKER_CLI_BUILD:-1}"
export BUILDKIT_PROGRESS="${BUILDKIT_PROGRESS:-plain}"

cd "${PROJECT_DIR}"

build_spring_image() {
  local build_args=(
    --build-arg BUILDKIT_INLINE_CACHE=1
    --tag "${SPRING_IMAGE}"
  )

  if docker image inspect "${SPRING_IMAGE}" >/dev/null 2>&1; then
    build_args+=(--cache-from "${SPRING_IMAGE}")
  fi

  docker build "${build_args[@]}" .
}

prune_old_build_cache() {
  echo "Pruning old Docker build artifacts..."
  rm -rf "${HOME}/.cache/startingblock-docker-build" >/dev/null 2>&1 || true
  docker image prune -f >/dev/null 2>&1 || true
  docker container prune -f >/dev/null 2>&1 || true
}

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

build_spring_image
docker compose "${COMPOSE_PROFILE_ARGS[@]}" up -d --no-build --force-recreate "spring-${TARGET}"

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

python3 - "${NGINX_CONF}" "${TARGET}" <<'PY'
import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
target = sys.argv[2]
content = path.read_text()
content = re.sub(r"server spring-(blue|green):8080;", f"server spring-{target}:8080;", content)
path.write_text(content)
PY

PROXY_STATUS="$(docker inspect -f '{{.State.Status}}' "${PROXY_CONTAINER}" 2>/dev/null || true)"
if [[ "${PROXY_STATUS}" == "running" ]] \
  && docker exec "${PROXY_CONTAINER}" nginx -t \
  && docker exec "${PROXY_CONTAINER}" nginx -s reload; then
  true
else
  docker compose up -d --build --force-recreate proxy
fi

echo "Traffic switched: spring-${OLD} -> spring-${TARGET}"

if [[ "${STOP_OLD}" == "true" ]]; then
  OLD_CONTAINER="startingblock-spring-${OLD}"
  if docker inspect "${OLD_CONTAINER}" >/dev/null 2>&1; then
    echo "Removing old container: ${OLD_CONTAINER}"
    docker rm -f "${OLD_CONTAINER}" >/dev/null
  else
    echo "Old container not found: ${OLD_CONTAINER}"
  fi
fi

if [[ "${PRUNE_OLD_BUILD_CACHE}" == "true" ]]; then
  prune_old_build_cache
fi
