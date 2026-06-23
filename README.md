# Starting Block Server

Spring Boot 서버와 Python AI/RAG CLI를 실행하고, proxy 컨테이너 안의 FalkorDB를 RAG 저장소로 사용하는 서버 프로젝트입니다.
외부로 공개되는 포트는 nginx 프록시의 `18200` 하나입니다.

## 실행 구조

- `startingblock-proxy`: 외부 요청을 받는 nginx 프록시 (`localhost:18200`)
- `startingblock-proxy` 내부 FalkorDB/Redis: RAG 그래프 저장소 + LLM 응답 진행 상태 저장소
- `startingblock-spring-blue`: Spring Boot + Python AI/RAG CLI
- `startingblock-spring-green`: 다음 배포를 위한 대기 컨테이너

MySQL, MinIO, Ollama는 서버에 설치된 인스턴스를 사용합니다. Redis는
`startingbloakc:nginx` 프록시 이미지 안의 FalkorDB Redis 프로토콜을 함께 사용합니다.
Docker Compose에서 별도 MySQL, MinIO, Qdrant, Redis, FalkorDB 컨테이너를 만들지 않습니다.

FalkorDB는 proxy 컨테이너 내부에서 실행되며 호스트 `/data/falkordb`에 영속 저장합니다.

## 환경 변수

```bash
cp .env.example .env
```

`.env`에서 아래 값을 서버 환경에 맞게 설정합니다.

- MySQL: `DB_HOST_FOR_CONTAINER`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- MinIO: `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET`
- Ollama: `OLLAMA_BASE_URL`, `OLLAMA_MODEL`
- Redis/FalkorDB: `REDIS_URL`, `FALKORDB_HOST`, `FALKORDB_PORT`, `FALKORDB_GRAPH_NAME`
- 외부 포트: `SERVER_PORT=18200`

컨테이너에서 서버에 설치된 MySQL, MinIO, Ollama로 접근할 때는
`host.docker.internal`을 사용합니다.

## 최초 실행

```bash
docker compose up -d --build proxy spring-blue
```

상태 확인:

```bash
curl http://localhost:18200/health
docker compose exec proxy redis-cli ping
docker compose exec proxy redis-cli GRAPH.LIST
```

`proxy`만 단독 실행하면 FalkorDB/Redis 상태 확인은 가능하지만, Spring 컨테이너가 없으므로
`/health`는 502로 응답할 수 있습니다.

## AI/RAG 초기화

LLM 채팅은 Ollama `generate` API를 사용합니다. 사용자별 요청은 서버에서 단일
prompt로 구성해 전달하므로 Ollama chat context를 공유하지 않습니다. 기본 모델은
`.env`의 아래 값입니다.

```env
OLLAMA_MODEL=gemma4:e4b
OLLAMA_KEEP_ALIVE=60s
OLLAMA_MODEL_IDLE_SECONDS=60
```

`/llm/chat` 응답 진행 상태는 Redis에 저장됩니다. SSE 연결이 끊겨도 같은
`thread_id`로 아래 API를 호출하면 현재 단계와 부분 응답을 조회할 수 있습니다.

```bash
curl "http://localhost:18200/llm/status?thread_id={thread_id}"
```

SSE를 다시 연결하려면 `/llm/stream`을 사용합니다. Redis에 남아 있는 이벤트를
처음부터 재생한 뒤, 진행 중인 생성에 이어 붙습니다.

```bash
curl -N "http://localhost:18200/llm/stream?thread_id={thread_id}"
curl -N "http://localhost:18200/llm/stream?thread_id={thread_id}&after_seq={last_seq}"
```

생성이 완료되면 Redis 이벤트는 짧은 TTL 뒤 정리되고, 이후 대화 내용은 DB에서
조회합니다.

```bash
curl "http://localhost:18200/llm/history?thread_id={thread_id}"
```

Ollama 동시 요청은 GPU 상태에 따라 동적으로 제한합니다. `/llm/chat` 요청은
Spring에서 `nvidia-smi`로 GPU free memory와 utilization을 확인한 뒤, 허용된
요청만 Python process로 실행합니다. 초과 요청은 SSE로 `dynamic_queue_waiting`
상태를 보내며 대기합니다.

튜닝 변수:

```env
LLM_DYNAMIC_CONCURRENCY_ENABLED=true
LLM_DYNAMIC_MAX_CONCURRENCY=0
LLM_DYNAMIC_REQUEST_MEMORY_MB=8192
LLM_GPU_MIN_FREE_MEMORY_MB=2048
LLM_GPU_MAX_UTILIZATION=92
LLM_DYNAMIC_WAIT_INTERVAL_MS=1000
LLM_GPU_WAIT_TIMEOUT_SECONDS=600
```

`LLM_DYNAMIC_MAX_CONCURRENCY=0`은 고정 상한 없이 GPU free memory 기반으로 동시
처리 수를 계산한다는 뜻입니다. 양수로 설정하면 그 값이 하드 상한이 됩니다.
`LLM_DYNAMIC_REQUEST_MEMORY_MB`는 요청 1개가 추가로 사용할 수 있다고 보는 GPU
메모리 예산입니다. OOM이 나면 값을 올리고, GPU 여유가 충분한데 큐가 길면 값을
낮추면 됩니다.

임베딩 모델 다운로드:

```bash
docker compose run --rm --entrypoint /opt/ai-rag-venv/bin/python spring-blue -m app.scripts.download_embedding_model
```

기존 `processed_file/{announcement_id}.txt` 파일을 MinIO와 FalkorDB에 백필:

```bash
docker compose exec spring-blue /opt/ai-rag-venv/bin/python -m app.scripts.migrate_processed_to_minio
docker compose exec spring-blue /opt/ai-rag-venv/bin/python -m app.scripts.backfill_embeddings --num-gpus 2 --announcement-batch-size 16 --prefetch-batches 1 --cuda-empty-cache-every 1
```

FalkorDB에 저장된 RAG graph 데이터를 모두 삭제하려면 먼저 dry-run으로 대상 graph를 확인합니다.
이 명령은 같은 Redis 인스턴스에 저장된 LLM 진행 상태 키는 지우지 않고 FalkorDB graph만 삭제합니다.

```bash
docker compose exec spring-blue /opt/ai-rag-venv/bin/python -m app.scripts.clear_falkordb_graph
```

확인 후 실제 삭제:

```bash
docker compose exec spring-blue /opt/ai-rag-venv/bin/python -m app.scripts.clear_falkordb_graph --yes
```

전체 재백필을 처음부터 다시 수행하려면 백필 checkpoint도 함께 삭제합니다.

```bash
docker compose exec spring-blue rm -f /app/ai-rag/app/data/backfill_falkordb.done
docker compose exec spring-blue /opt/ai-rag-venv/bin/python -m app.scripts.backfill_embeddings --num-gpus 2 --announcement-batch-size 16 --prefetch-batches 1 --cuda-empty-cache-every 1
```

FalkorDB 전환 전 Qdrant와 검색 결과를 비교하려면 새 코드가 올라간 타겟 컨테이너에서, 아직 떠 있는 이전 Spring 컨테이너의 Qdrant URL을 지정해 아래 스크립트를 실행합니다.

```bash
docker compose exec spring-green env QDRANT_URL=http://startingblock-spring-blue:6333 \
  /opt/ai-rag-venv/bin/python -m app.scripts.compare_falkordb_qdrant --limit 5 --backfill-falkor
```

FalkorDB 검색 결과가 더 낫다고 판단되어 전환이 완료되면 기존 Qdrant 데이터는 호스트에서 삭제합니다.

```bash
sudo rm -rf /data/qdrant-blue /data/qdrant-green
```

## 무중단 배포

배포는 아래 명령 하나로 실행합니다.

```bash
./deploy/blue-green-deploy.sh
```

스크립트는 현재 nginx가 바라보는 색상을 확인한 뒤 자동으로 반대쪽 컨테이너를
빌드하고 실행합니다.

- 현재 blue가 활성 상태이면 green을 빌드한 뒤 green으로 전환
- 현재 green이 활성 상태이면 blue를 빌드한 뒤 blue로 전환

새 컨테이너의 `/health`가 정상 응답해야 트래픽을 전환합니다. 전환 후 기존
컨테이너는 빠른 롤백을 위해 기본적으로 유지합니다.

타겟 컨테이너에서 MinIO 마이그레이션과 FalkorDB 백필까지 실행한 뒤 전환하려면:

```bash
RUN_BACKFILL=true ./deploy/blue-green-deploy.sh
```

전환 후 이전 컨테이너를 중지하려면:

```bash
STOP_OLD=true ./deploy/blue-green-deploy.sh
```

## Cloudflare

`startingblock-api.newlearn.ai.kr`는 아래 주소로 연결합니다.

```text
http://localhost:18200
```

외부로 노출하지 않아야 하는 포트:

- 서버 MySQL `3306`
- proxy 내부 FalkorDB/Redis `6379`
