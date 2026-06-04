import io
import json
import select
import sys
import traceback
import time
from contextlib import redirect_stderr, redirect_stdout
from typing import Any

from app import cli
from app.core.config import OLLAMA_IDLE_SWEEP_INTERVAL_SECONDS, OLLAMA_MODEL_IDLE_SECONDS


ORIGINAL_STDIN = sys.stdin
ORIGINAL_STDOUT = sys.stdout
ORIGINAL_STDERR = sys.stderr
ACTIVE_REQUESTS = 0
LAST_COMPLETED_AT = time.monotonic()
LAST_IDLE_SWEEP_AT = 0.0


def _write_frame(frame: dict[str, Any]) -> None:
    ORIGINAL_STDOUT.write(json.dumps(frame, ensure_ascii=False) + "\n")
    ORIGINAL_STDOUT.flush()


class StreamingStdout(io.StringIO):
    def __init__(self, request_id: str):
        super().__init__()
        self.request_id = request_id

    def flush(self) -> None:
        value = self.getvalue()
        if value:
            self.seek(0)
            self.truncate(0)
            _write_frame({"id": self.request_id, "type": "stdout", "data": value})
        super().flush()


def _run_cli(request: dict[str, Any]) -> None:
    global ACTIVE_REQUESTS, LAST_COMPLETED_AT
    request_id = str(request.get("id") or "")
    command = request.get("command") or []
    stdin = request.get("stdin") or ""
    stream = bool(request.get("stream"))

    old_argv = sys.argv
    old_stdin = sys.stdin

    sys.argv = ["app.cli", *command]
    sys.stdin = io.StringIO(stdin)
    ACTIVE_REQUESTS += 1

    stderr_buffer = io.StringIO()
    exit_code = 1

    try:
        if stream:
            stdout_buffer = StreamingStdout(request_id)
            with redirect_stdout(stdout_buffer), redirect_stderr(stderr_buffer):
                try:
                    exit_code = cli.main()
                finally:
                    stdout_buffer.flush()
        else:
            stdout_buffer = io.StringIO()
            with redirect_stdout(stdout_buffer), redirect_stderr(stderr_buffer):
                exit_code = cli.main()

            _write_frame(
                {
                    "id": request_id,
                    "type": "result",
                    "exit": exit_code,
                    "stdout": stdout_buffer.getvalue(),
                    "stderr": stderr_buffer.getvalue(),
                }
            )
            return
    except SystemExit as error:
        exit_code = int(error.code or 0)
    except Exception:
        traceback.print_exc(file=stderr_buffer)
    finally:
        sys.argv = old_argv
        sys.stdin = old_stdin
        ACTIVE_REQUESTS = max(0, ACTIVE_REQUESTS - 1)
        LAST_COMPLETED_AT = time.monotonic()

    _write_frame(
        {
            "id": request_id,
            "type": "end" if stream else "result",
            "exit": exit_code,
            "stderr": stderr_buffer.getvalue(),
        }
    )
    LAST_COMPLETED_AT = time.monotonic()


def _unload_ollama_if_idle() -> None:
    global LAST_IDLE_SWEEP_AT
    if OLLAMA_MODEL_IDLE_SECONDS <= 0:
        return

    now = time.monotonic()
    if now - LAST_IDLE_SWEEP_AT < OLLAMA_IDLE_SWEEP_INTERVAL_SECONDS:
        return
    LAST_IDLE_SWEEP_AT = now

    if ACTIVE_REQUESTS > 0:
        return

    if now - LAST_COMPLETED_AT < OLLAMA_MODEL_IDLE_SECONDS:
        return

    try:
        import asyncio

        from app.api.llm.client import create_llm_client

        client = create_llm_client()
        asyncio.run(client.unload_if_idle(OLLAMA_MODEL_IDLE_SECONDS))
    except Exception:
        traceback.print_exc(file=ORIGINAL_STDERR)


def main() -> int:
    while True:
        readable, _, _ = select.select([ORIGINAL_STDIN], [], [], OLLAMA_IDLE_SWEEP_INTERVAL_SECONDS)
        if not readable:
            _unload_ollama_if_idle()
            continue

        line = ORIGINAL_STDIN.readline()
        if line == "":
            break
        line = line.strip()
        if not line:
            continue
        try:
            request = json.loads(line)
        except json.JSONDecodeError as error:
            _write_frame({"id": "", "type": "result", "exit": 1, "stdout": "", "stderr": str(error)})
            continue

        _run_cli(request)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
