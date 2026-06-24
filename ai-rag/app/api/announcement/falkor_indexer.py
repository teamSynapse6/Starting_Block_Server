import hashlib
import re
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

from falkordb import FalkorDB
from redis.exceptions import ResponseError
from langchain_core.documents import Document
from langchain_huggingface import HuggingFaceEmbeddings
from langchain_text_splitters import RecursiveCharacterTextSplitter

from app.core.config import (
    EMBEDDING_ENCODE_BATCH_SIZE,
    EMBEDDING_MODEL_LOCAL_PATH,
    EMBEDDING_MODEL_REPO_ID,
    FALKORDB_GRAPH_NAME,
    FALKORDB_HOST,
    FALKORDB_KEYWORD_CANDIDATE_LIMIT,
    FALKORDB_PASSWORD,
    FALKORDB_PORT,
    FALKORDB_QUERY_TIMEOUT_MS,
    FALKORDB_VECTOR_CANDIDATE_MULTIPLIER,
    INDEXING_BATCH_SIZE,
    RAG_CHUNK_OVERLAP,
    RAG_CHUNK_SIZE,
)


class AnnouncementFalkorIndexer:
    def __init__(self, embedding_device: str = "auto"):
        self.graph_name = FALKORDB_GRAPH_NAME
        self.model_local_path = Path(EMBEDDING_MODEL_LOCAL_PATH)
        self._validate_local_model_path()

        resolved_device = self._resolve_embedding_device(embedding_device)
        model_kwargs = {"local_files_only": True}
        if resolved_device != "auto":
            model_kwargs["device"] = resolved_device

        self.embeddings = HuggingFaceEmbeddings(
            model_name=str(self.model_local_path),
            model_kwargs=model_kwargs,
            encode_kwargs={
                "normalize_embeddings": True,
                "batch_size": EMBEDDING_ENCODE_BATCH_SIZE,
            },
        )
        self.db = FalkorDB(
            host=FALKORDB_HOST,
            port=FALKORDB_PORT,
            password=FALKORDB_PASSWORD or None,
            socket_timeout=max(FALKORDB_QUERY_TIMEOUT_MS / 1000, 1),
            socket_connect_timeout=10,
        )
        self.graph = self.db.select_graph(self.graph_name)
        self.splitter = RecursiveCharacterTextSplitter(
            chunk_size=RAG_CHUNK_SIZE,
            chunk_overlap=RAG_CHUNK_OVERLAP,
            separators=["\n\n", "\n", ". ", " "],
        )
        self.batch_size = max(INDEXING_BATCH_SIZE, 1)
        self._vector_dimension: int | None = None
        self._ready = False

    def _resolve_embedding_device(self, embedding_device: str) -> str:
        normalized = (embedding_device or "auto").strip().lower()
        if normalized in {"cpu", "mps"}:
            return normalized

        if normalized.startswith("cuda:"):
            try:
                import torch

                idx = int(normalized.split(":", 1)[1])
                if torch.cuda.is_available() and idx < torch.cuda.device_count():
                    return normalized
            except Exception:
                pass
            return "cpu"

        if normalized in {"cuda", "gpu", "auto"}:
            try:
                import torch

                if torch.cuda.is_available():
                    return "cuda:0"
            except Exception:
                pass
            return "cpu"

        return normalized

    def _validate_local_model_path(self):
        if not self.model_local_path.exists() or not self.model_local_path.is_dir():
            raise RuntimeError(
                "로컬 임베딩 모델 경로를 찾을 수 없습니다. "
                "`python -m app.scripts.download_embedding_model` 실행 후 다시 시작하세요. "
                f"(path={self.model_local_path})"
            )

        required_files = {"config.json", "modules.json"}
        missing = [name for name in required_files if not (self.model_local_path / name).exists()]
        if missing:
            raise RuntimeError(
                "로컬 임베딩 모델 파일이 누락되었습니다. "
                "`python -m app.scripts.download_embedding_model` 실행 후 다시 시작하세요. "
                f"(missing={', '.join(missing)})"
            )

    def _query(self, cypher: str, params: dict | None = None, read_only: bool = False):
        if read_only:
            return self.graph.ro_query(cypher, params=params, timeout=FALKORDB_QUERY_TIMEOUT_MS)
        return self.graph.query(cypher, params=params, timeout=FALKORDB_QUERY_TIMEOUT_MS)

    def ensure_ready(self):
        if self._ready:
            return

        self._ensure_graph_exists()
        if self._vector_dimension is None:
            self._vector_dimension = len(self.embeddings.embed_query("벡터 차원 초기화"))

        self._create_index("CREATE INDEX FOR (a:Announcement) ON (a.announcement_id)")
        self._create_index("CREATE INDEX FOR (c:Chunk) ON (c.announcement_id)")
        self._create_index("CREATE INDEX FOR (c:Chunk) ON (c.chunk_uid)")
        self._create_index("CREATE INDEX FOR (k:Keyword) ON (k.term)")
        self._create_index(
            f"CREATE VECTOR INDEX FOR (c:Chunk) ON (c.embedding) "
            f"OPTIONS {{dimension:{self._vector_dimension}, similarityFunction:'cosine', M:32, efConstruction:300}}"
        )
        self._ready = True

    def _create_index(self, cypher: str, params: dict | None = None):
        try:
            self._query(cypher, params=params)
        except Exception as error:
            message = str(error).lower()
            if "already" not in message and "exists" not in message and "indexed" not in message:
                raise

    def _ensure_graph_exists(self):
        try:
            self._query("RETURN 1")
        except ResponseError as error:
            if "empty key" not in str(error).lower():
                raise
            self._query("CREATE (:__GraphBootstrap {created_at:$created_at})", {"created_at": datetime.now(timezone.utc).isoformat()})
            self._query("MATCH (n:__GraphBootstrap) DELETE n")

    def _build_documents(self, announcement_id: int, text: str) -> tuple[list[Document], list[str]]:
        now = datetime.now(timezone.utc).isoformat()
        chunks = [chunk.strip() for chunk in self.splitter.split_text(text) if chunk and chunk.strip()]

        documents: list[Document] = []
        ids: list[str] = []
        for chunk_id, chunk in enumerate(chunks):
            content_hash = hashlib.sha256(chunk.encode("utf-8")).hexdigest()
            chunk_uid = f"{announcement_id}:{chunk_id}"
            metadata = {
                "announcement_id": announcement_id,
                "chunk_id": chunk_id,
                "chunk_uid": chunk_uid,
                "source": "minio_processed",
                "content_hash": content_hash,
                "embedding_model": EMBEDDING_MODEL_REPO_ID,
                "created_at": now,
            }
            documents.append(Document(page_content=chunk, metadata=metadata))
            ids.append(chunk_uid)

        return documents, ids

    def _keywords(self, text: str) -> list[str]:
        words = re.findall(r"[0-9A-Za-z가-힣]{2,}", text.lower())
        stopwords = {"및", "또는", "그리고", "대한", "관련", "지원", "사업", "공고"}
        seen: set[str] = set()
        keywords: list[str] = []
        for word in words:
            if word in stopwords or word in seen:
                continue
            seen.add(word)
            keywords.append(word)
            if len(keywords) >= 24:
                break
        return keywords

    def _clean_property_text(self, text: str) -> str:
        return "".join(
            char if char in "\n\r\t" or ord(char) >= 32 else " "
            for char in text
        )

    def delete_announcement(self, announcement_id: int) -> None:
        self.delete_announcements([announcement_id])

    def delete_announcements(self, announcement_ids: list[int]) -> None:
        if not announcement_ids:
            return

        self._query(
            """
            UNWIND $announcement_ids AS announcement_id
            MATCH (:Announcement {announcement_id:announcement_id})-[:HAS_CHUNK]->(c:Chunk)
            DETACH DELETE c
            """,
            {"announcement_ids": announcement_ids},
        )
        self._query(
            """
            UNWIND $announcement_ids AS announcement_id
            MATCH (a:Announcement {announcement_id:announcement_id})
            DETACH DELETE a
            """,
            {"announcement_ids": announcement_ids},
        )
        self._query(
            """
            MATCH (k:Keyword)
            WHERE NOT (k)<-[:MENTIONS]-(:Chunk)
            DELETE k
            """
        )

    def upsert_announcement(self, announcement_id: int, text: str) -> int:
        return self.upsert_announcements([(announcement_id, text)]).get(announcement_id, 0)

    def upsert_announcements(self, items: list[tuple[int, str]]) -> dict[int, int]:
        self.ensure_ready()

        documents: list[Document] = []
        counts: dict[int, int] = {}
        announcement_ids: list[int] = []
        for announcement_id, text in items:
            announcement_ids.append(announcement_id)
            item_documents, _ = self._build_documents(announcement_id, text)
            counts[announcement_id] = len(item_documents)
            documents.extend(item_documents)

        self.delete_announcements(announcement_ids)
        if not documents:
            return counts

        embeddings = self.embeddings.embed_documents([doc.page_content for doc in documents])

        rows = [
            {
                "announcement_id": doc.metadata["announcement_id"],
                "chunk_id": doc.metadata["chunk_id"],
                "chunk_uid": doc.metadata["chunk_uid"],
                "text": self._clean_property_text(doc.page_content),
                "content_hash": doc.metadata["content_hash"],
                "embedding_model": doc.metadata["embedding_model"],
                "created_at": doc.metadata["created_at"],
                "embedding": [float(value) for value in embedding],
                "keywords": self._keywords(doc.page_content),
            }
            for doc, embedding in zip(documents, embeddings)
        ]
        self._write_rows(rows, self.batch_size)

        return counts

    def _write_rows(self, rows: list[dict], batch_size: int):
        if not rows:
            return

        batch_size = max(1, min(batch_size, len(rows)))
        for start in range(0, len(rows), batch_size):
            batch = rows[start:start + batch_size]
            try:
                self._write_row_batch(batch)
            except Exception as error:
                message = str(error).lower()
                if "parse query parameter" not in message:
                    raise
                if batch_size <= 1:
                    self._write_single_row(batch[0])
                    continue
                self._write_rows(batch, max(1, batch_size // 2))

    def _write_row_batch(self, rows: list[dict]):
        self._query(
            """
            UNWIND $rows AS row
            MERGE (a:Announcement {announcement_id:row.announcement_id})
            CREATE (c:Chunk {
                announcement_id: row.announcement_id,
                chunk_id: row.chunk_id,
                chunk_uid: row.chunk_uid,
                text: row.text,
                content_hash: row.content_hash,
                embedding_model: row.embedding_model,
                created_at: row.created_at,
                embedding: vecf32(row.embedding)
            })
            CREATE (a)-[:HAS_CHUNK]->(c)
            WITH c, row
            UNWIND row.keywords AS term
            MERGE (k:Keyword {term:term})
            CREATE (c)-[:MENTIONS]->(k)
            """,
            {"rows": rows},
        )

    def _write_single_row(self, row: dict):
        self._query(
            """
            MERGE (a:Announcement {announcement_id:$announcement_id})
            CREATE (c:Chunk {
                announcement_id: $announcement_id,
                chunk_id: $chunk_id,
                chunk_uid: $chunk_uid,
                text: $text,
                content_hash: $content_hash,
                embedding_model: $embedding_model,
                created_at: $created_at,
                embedding: vecf32($embedding)
            })
            CREATE (a)-[:HAS_CHUNK]->(c)
            WITH c
            UNWIND $keywords AS term
            MERGE (k:Keyword {term:term})
            CREATE (c)-[:MENTIONS]->(k)
            """,
            row,
        )

    def search_chunks(self, announcement_id: int, query: str, top_k: int) -> list[Document]:
        self.ensure_ready()
        top_k = max(int(top_k), 1)
        candidate_k = max(top_k * max(FALKORDB_VECTOR_CANDIDATE_MULTIPLIER, 1), top_k)
        query_vector = [float(value) for value in self.embeddings.embed_query(query)]

        scores: dict[str, float] = defaultdict(float)
        payloads: dict[str, tuple[str, dict]] = {}

        vector_result = self._query(
            """
            CALL db.idx.vector.queryNodes('Chunk', 'embedding', $candidate_k, vecf32($query_vector))
            YIELD node, score
            WHERE node.announcement_id = $announcement_id
            RETURN node.chunk_uid, node.chunk_id, node.text, node.content_hash, node.embedding_model, node.created_at, score
            """,
            {
                "announcement_id": announcement_id,
                "candidate_k": candidate_k,
                "query_vector": query_vector,
            },
            read_only=True,
        )
        for row in vector_result.result_set:
            chunk_uid = row[0]
            metadata = self._metadata_from_row(announcement_id, row)
            payloads[chunk_uid] = (row[2], metadata)
            scores[chunk_uid] += float(row[6]) * 0.75

        keywords = self._keywords(query)
        if keywords:
            keyword_result = self._query(
                """
                MATCH (a:Announcement {announcement_id:$announcement_id})-[:HAS_CHUNK]->(c:Chunk)-[:MENTIONS]->(k:Keyword)
                WHERE k.term IN $keywords
                RETURN c.chunk_uid, c.chunk_id, c.text, c.content_hash, c.embedding_model, c.created_at, count(k) AS matches
                ORDER BY matches DESC, c.chunk_id ASC
                LIMIT $limit
                """,
                {
                    "announcement_id": announcement_id,
                    "keywords": keywords,
                    "limit": FALKORDB_KEYWORD_CANDIDATE_LIMIT,
                },
                read_only=True,
            )
            for row in keyword_result.result_set:
                chunk_uid = row[0]
                metadata = self._metadata_from_row(announcement_id, row)
                payloads[chunk_uid] = (row[2], metadata)
                scores[chunk_uid] += float(row[6]) * 0.25

        ranked = sorted(scores.items(), key=lambda item: item[1], reverse=True)[:top_k]
        return [
            Document(page_content=payloads[chunk_uid][0], metadata=payloads[chunk_uid][1])
            for chunk_uid, _score in ranked
            if chunk_uid in payloads
        ]

    def _metadata_from_row(self, announcement_id: int, row) -> dict:
        return {
            "announcement_id": announcement_id,
            "chunk_uid": row[0],
            "chunk_id": row[1],
            "source": "minio_processed",
            "content_hash": row[3],
            "embedding_model": row[4],
            "created_at": row[5],
        }

    def announcement_vector_count(self, announcement_id: int) -> int:
        result = self._query(
            "MATCH (:Announcement {announcement_id:$announcement_id})-[:HAS_CHUNK]->(c:Chunk) RETURN count(c)",
            {"announcement_id": announcement_id},
            read_only=True,
        )
        if not result.result_set:
            return 0
        return int(result.result_set[0][0])

    def has_announcement_vectors(self, announcement_id: int) -> bool:
        return self.announcement_vector_count(announcement_id) > 0
