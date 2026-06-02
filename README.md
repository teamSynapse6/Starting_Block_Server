# Starting Block Server

Spring Boot 서버 안에서 Python AI/RAG CLI와 Qdrant를 함께 실행하는 서버 프로젝트입니다.
외부로 공개되는 포트는 nginx 프록시의 `18200` 하나입니다.

## 실행 구조

- `startingblock-proxy`: 외부 요청을 받는 nginx 프록시 (`localhost:18200`)
- `startingblock-spring-blue`: Spring Boot + Python AI/RAG CLI + Qdrant
- `startingblock-spring-green`: 다음 배포를 위한 대기 컨테이너

MySQL, MinIO, Ollama는 서버에 설치된 인스턴스를 사용합니다. Docker Compose에서
별도 MySQL, MinIO, Qdrant 컨테이너를 만들지 않습니다.

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
```

컨테이너 내부 Qdrant 확인:

```bash
docker compose exec spring-blue curl http://127.0.0.1:6333/healthz
docker compose exec spring-blue curl http://127.0.0.1:6333/collections
```

## AI/RAG 초기화

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
