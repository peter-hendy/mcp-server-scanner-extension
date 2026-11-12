from __future__ import annotations

import itertools
import json
import threading
import time
from collections import deque
from pathlib import Path
from typing import Any

from starlette.types import ASGIApp, Message, Receive, Scope, Send

from mcp_test_server.protocol.json_leaves import leaf_paths

INSPECTION_PREFIX = "/_test"
_CAPACITY = 10_000


class RequestLog:
    def __init__(self, file_path: Path | None = None) -> None:
        self._entries: deque[dict[str, Any]] = deque(maxlen=_CAPACITY)
        self._sequence = itertools.count(1)
        self._file_path = file_path
        self._lock = threading.Lock()

    def record(self, entry: dict[str, Any]) -> None:
        with self._lock:
            entry["seq"] = next(self._sequence)
            self._entries.append(entry)
            if self._file_path is not None:
                with self._file_path.open("a", encoding="utf-8") as handle:
                    handle.write(json.dumps(entry) + "\n")

    def entries(self) -> list[dict[str, Any]]:
        with self._lock:
            return list(self._entries)

    def clear(self) -> None:
        with self._lock:
            self._entries.clear()


def _is_inspection(path: str) -> bool:
    return path == INSPECTION_PREFIX or path.startswith(INSPECTION_PREFIX + "/")


def describe_messages(body: bytes) -> list[dict[str, Any]]:
    try:
        decoded = json.loads(body) if body else None
    except ValueError:
        return []
    messages = decoded if isinstance(decoded, list) else [decoded]
    return [_describe(message) for message in messages if isinstance(message, dict)]


def _describe(message: dict[str, Any]) -> dict[str, Any]:
    params = message.get("params")
    return {
        "method": message.get("method"),
        "id": message.get("id"),
        "notification": "method" in message and "id" not in message,
        "params": params,
        "param_paths": leaf_paths(params, "params") if params is not None else [],
    }


class RequestLogMiddleware:
    def __init__(self, app: ASGIApp, log: RequestLog) -> None:
        self._app = app
        self._log = log

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http" or _is_inspection(scope["path"]):
            await self._app(scope, receive, send)
            return
        body = await self._read_body(receive)
        entry = self._entry(scope, body)
        try:
            await self._app(scope, self._replay(body, receive), self._recording_send(send, entry))
        finally:
            if "status" not in entry:
                entry["status"] = 500
                self._log.record(entry)

    async def _read_body(self, receive: Receive) -> bytes:
        chunks: list[bytes] = []
        while True:
            message = await receive()
            chunks.append(message.get("body", b""))
            if not message.get("more_body", False):
                return b"".join(chunks)

    @staticmethod
    def _replay(body: bytes, downstream: Receive) -> Receive:
        delivered = False

        async def receive() -> Message:
            nonlocal delivered
            if delivered:
                return await downstream()
            delivered = True
            return {"type": "http.request", "body": body, "more_body": False}

        return receive

    def _recording_send(self, send: Send, entry: dict[str, Any]) -> Send:
        async def recording_send(message: Message) -> None:
            if message["type"] == "http.response.start":
                entry["status"] = message["status"]
                self._log.record(entry)
            await send(message)

        return recording_send

    @staticmethod
    def _entry(scope: Scope, body: bytes) -> dict[str, Any]:
        return {
            "time": time.time(),
            "http_method": scope["method"],
            "path": scope["path"],
            "query": scope.get("query_string", b"").decode("latin-1"),
            "headers": [[k.decode("latin-1"), v.decode("latin-1")] for k, v in scope["headers"]],
            "messages": describe_messages(body),
        }
