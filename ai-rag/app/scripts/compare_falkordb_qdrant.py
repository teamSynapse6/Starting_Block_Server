import argparse
import json

from app.api.announcement.falkor_indexer import AnnouncementFalkorIndexer
from app.api.announcement.vector_indexer import AnnouncementVectorIndexer
from app.core.config import EMBEDDING_DEVICE, RAG_TOP_K
from app.core.storage import MinioStorage


def parse_args():
    parser = argparse.ArgumentParser(description="같은 processed 파일에 대해 Qdrant와 FalkorDB 검색 결과를 비교합니다.")
    parser.add_argument("--ids", type=str, default="", help="쉼표 구분 announcement id 목록")
    parser.add_argument("--limit", type=int, default=5, help="ids 미지정 시 비교할 processed 파일 개수")
    parser.add_argument("--queries", type=str, default="", help="세미콜론으로 구분한 비교 질문 목록")
    parser.add_argument("--top-k", type=int, default=RAG_TOP_K)
    parser.add_argument("--backfill-falkor", action="store_true", help="비교 전 FalkorDB에 대상 processed 파일을 upsert")
    return parser.parse_args()


def _target_ids(storage: MinioStorage, ids_arg: str, limit: int) -> list[str]:
    if ids_arg.strip():
        return [item.strip() for item in ids_arg.split(",") if item.strip()]
    ids = storage.list_processed_ids()
    return ids[:limit] if limit > 0 else ids


def _queries(raw: str) -> list[str]:
    queries = [item.strip() for item in raw.split(";") if item.strip()]
    if queries:
        return queries
    return [
        "지원 대상은 누구인가요?",
        "신청 기간은 언제인가요?",
        "신청 방법과 제출 서류를 알려줘",
        "지원 금액과 선정 규모를 알려줘",
        "문의처는 어디인가요?",
    ]


def _serialize(chunks) -> list[dict]:
    result = []
    for chunk in chunks:
        result.append(
            {
                "chunk_id": chunk.metadata.get("chunk_id"),
                "chunk_uid": chunk.metadata.get("chunk_uid"),
                "preview": chunk.page_content[:220],
            }
        )
    return result


def main():
    args = parse_args()
    storage = MinioStorage()
    storage.ensure_bucket()

    target_ids = _target_ids(storage, args.ids, args.limit)
    queries = _queries(args.queries)

    qdrant = AnnouncementVectorIndexer(EMBEDDING_DEVICE)
    qdrant.ensure_ready()
    falkor = AnnouncementFalkorIndexer(EMBEDDING_DEVICE)
    falkor.ensure_ready()

    report = []
    for raw_id in target_ids:
        announcement_id = int(raw_id)
        text = storage.get_processed_text(announcement_id)
        if text is None:
            report.append({"announcement_id": announcement_id, "status": "missing_processed_text"})
            continue

        if args.backfill_falkor and not falkor.has_announcement_vectors(announcement_id):
            falkor.upsert_announcement(announcement_id, text)

        item = {"announcement_id": announcement_id, "queries": []}
        for query in queries:
            qdrant_chunks = qdrant.search_chunks(announcement_id, query, args.top_k)
            falkor_chunks = falkor.search_chunks(announcement_id, query, args.top_k)
            qdrant_ids = {chunk.metadata.get("chunk_uid") for chunk in qdrant_chunks}
            falkor_ids = {chunk.metadata.get("chunk_uid") for chunk in falkor_chunks}
            overlap = len(qdrant_ids & falkor_ids)
            item["queries"].append(
                {
                    "query": query,
                    "qdrant_count": len(qdrant_chunks),
                    "falkordb_count": len(falkor_chunks),
                    "overlap": overlap,
                    "qdrant": _serialize(qdrant_chunks),
                    "falkordb": _serialize(falkor_chunks),
                }
            )
        report.append(item)

    print(json.dumps({"target_ids": target_ids, "queries": queries, "results": report}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
