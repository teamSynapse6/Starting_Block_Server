# Starting Block Server

Spring Boot 서버 안에서 Python AI/RAG CLI와 Qdrant를 함께 실행하는 서버 프로젝트입니다.
외부로 공개되는 포트는 nginx 프록시의 `18200` 하나입니다.

## 실행 구조

- `startingblock-proxy`: 외부 요청을 받는 nginx 프록시 (`localhost:18200`)
- `startingblock-proxy` 내부 Redis: LLM 응답 진행 상태 저장소
- `startingblock-spring-blue`: Spring Boot + Python AI/RAG CLI + Qdrant
- `startingblock-spring-green`: 다음 배포를 위한 대기 컨테이너

MySQL, MinIO, Ollama는 서버에 설치된 인스턴스를 사용합니다. Redis는
`startingbloakc:nginx` 프록시 이미지 안에서 함께 실행합니다. Docker Compose에서
별도 MySQL, MinIO, Qdrant, Redis 컨테이너를 만들지 않습니다.

Qdrant는 Spring 컨테이너 내부에서 실행되며 색상별로 저장 경로를 분리합니다.

- blue: `/data/qdrant-blue`
- green: `/data/qdrant-green`

## 환경 변수

```bash
cp .env.example .env
```

`.env`에서 아래 값을 서버 환경에 맞게 설정합니다.

- MySQL: `DB_HOST_FOR_CONTAINER`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
- MinIO: `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET`
- Ollama: `OLLAMA_BASE_URL`, `OLLAMA_MODEL`
- Redis: `REDIS_URL`
- 외부 포트: `SERVER_PORT=18200`

컨테이너에서 서버에 설치된 MySQL, MinIO, Ollama로 접근할 때는
`host.docker.internal`을 사용합니다.

## 최초 실행

```bash
docker compose up -d --build proxy
```

상태 확인:

```bash
curl http://localhost:18200/health
docker compose exec proxy redis-cli ping
```

컨테이너 내부 Qdrant 확인:

```bash
docker compose exec spring-blue curl http://127.0.0.1:6333/healthz
docker compose exec spring-blue curl http://127.0.0.1:6333/collections
```

## AI/RAG 초기화

LLM 채팅은 Ollama를 사용합니다. 기본 모델은 `.env`의 아래 값입니다.

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

기존 `processed_file/{announcement_id}.txt` 파일을 MinIO와 Qdrant에 백필:

```bash
docker compose exec spring-blue /opt/ai-rag-venv/bin/python -m app.scripts.migrate_processed_to_minio
docker compose exec spring-blue /opt/ai-rag-venv/bin/python -m app.scripts.backfill_embeddings
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

타겟 컨테이너에서 MinIO 마이그레이션과 Qdrant 백필까지 실행한 뒤 전환하려면:

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
- 컨테이너 내부 Qdrant `6333`
