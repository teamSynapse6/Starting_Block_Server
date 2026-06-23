import argparse

from falkordb import FalkorDB
from redis.exceptions import ResponseError

from app.core.config import (
    FALKORDB_GRAPH_NAME,
    FALKORDB_HOST,
    FALKORDB_PASSWORD,
    FALKORDB_PORT,
    FALKORDB_QUERY_TIMEOUT_MS,
)


def parse_args():
    parser = argparse.ArgumentParser(description="FalkorDB RAG graph 데이터를 삭제합니다.")
    parser.add_argument("--graph", type=str, default=FALKORDB_GRAPH_NAME, help="삭제할 FalkorDB graph 이름")
    parser.add_argument("--yes", action="store_true", help="실제 삭제를 수행합니다. 없으면 dry-run")
    return parser.parse_args()


def _connect() -> FalkorDB:
    return FalkorDB(
        host=FALKORDB_HOST,
        port=FALKORDB_PORT,
        password=FALKORDB_PASSWORD or None,
        socket_timeout=max(FALKORDB_QUERY_TIMEOUT_MS / 1000, 1),
        socket_connect_timeout=10,
    )


def _graph_names(db: FalkorDB) -> list[str]:
    names = db.execute_command("GRAPH.LIST")
    return [
        name.decode("utf-8") if isinstance(name, bytes) else str(name)
        for name in names
    ]


def main():
    args = parse_args()
    db = _connect()
    graph_name = args.graph.strip()
    if not graph_name:
        raise SystemExit("--graph 값이 비어 있습니다.")

    names = _graph_names(db)
    exists = graph_name in names
    print(f"target_graph={graph_name}")
    print(f"existing_graphs={','.join(names) if names else '(none)'}")

    if not exists:
        print("status=skipped reason=graph-not-found")
        return

    if not args.yes:
        print("status=dry-run")
        print("실제 삭제하려면 --yes 옵션을 추가하세요.")
        return

    graph = db.select_graph(graph_name)
    try:
        graph.delete()
    except ResponseError as error:
        if "does not exist" not in str(error).lower() and "not found" not in str(error).lower():
            raise

    names_after = _graph_names(db)
    print(f"status=deleted graph={graph_name}")
    print(f"remaining_graphs={','.join(names_after) if names_after else '(none)'}")


if __name__ == "__main__":
    main()
