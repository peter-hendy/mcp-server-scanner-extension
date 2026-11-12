from __future__ import annotations

import base64
import hashlib
import secrets
import socket
import threading
import time
from collections.abc import Iterator
from contextlib import contextmanager
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any
from urllib.parse import urlencode

import anyio
import httpx
import uvicorn
from starlette.testclient import TestClient

from mcp_test_server.app import ServerAssembly
from mcp_test_server.settings import ServerSettings

PROTOCOL_VERSION = "2025-06-18"
BASE_URL = "http://testserver"


@dataclass
class RecordingGetter:
    urls: list[str] = field(default_factory=list)

    async def get(self, url: str) -> str:
        self.urls.append(url)
        return f"fetched {url}"


class McpSession:
    def __init__(self, client: TestClient, headers: dict[str, str] | None = None, path: str = "/mcp") -> None:
        self.client = client
        self.headers = {"accept": "application/json, text/event-stream", **(headers or {})}
        self.path = path
        self._next_id = 0

    def initialize(self, **params: Any) -> httpx.Response:
        body = {"protocolVersion": PROTOCOL_VERSION, "capabilities": {},
                "clientInfo": {"name": "pytest", "version": "1"}, **params}
        response = self.post(self.message("initialize", body))
        session_id = response.headers.get("mcp-session-id")
        if session_id:
            self.headers["mcp-session-id"] = session_id
        return response

    def message(self, method: str, params: dict[str, Any] | None = None, notification: bool = False) -> dict[str, Any]:
        message: dict[str, Any] = {"jsonrpc": "2.0", "method": method}
        if params is not None:
            message["params"] = params
        if not notification:
            self._next_id += 1
            message["id"] = self._next_id
        return message

    def post(self, body: Any, headers: dict[str, str] | None = None) -> httpx.Response:
        return self.client.post(self.path, json=body, headers={**self.headers, **(headers or {})})

    def request(self, method: str, params: dict[str, Any] | None = None, **headers: str) -> dict[str, Any]:
        response = self.post(self.message(method, params), headers)
        assert response.status_code == 200, response.text
        payload: dict[str, Any] = response.json()
        return payload

    def result(self, method: str, params: dict[str, Any] | None = None, **headers: str) -> dict[str, Any]:
        payload = self.request(method, params, **headers)
        assert "result" in payload, payload
        result: dict[str, Any] = payload["result"]
        return result

    def call_tool(self, name: str, arguments: dict[str, Any], **headers: str) -> dict[str, Any]:
        return self.result("tools/call", {"name": name, "arguments": arguments}, **headers)

    def notify(self, method: str, params: dict[str, Any] | None = None) -> httpx.Response:
        return self.post(self.message(method, params, notification=True))


def tool_text(result: dict[str, Any]) -> str:
    return str(result["content"][0]["text"])


def resource_text(result: dict[str, Any]) -> str:
    return str(result["contents"][0]["text"])


def prompt_text(result: dict[str, Any]) -> str:
    return str(result["messages"][0]["content"]["text"])


class TestServer:
    __test__ = False

    def __init__(self, tmp_path: Path, **overrides: Any) -> None:
        raw = {"public_url": BASE_URL, "files_dir": str(tmp_path / "files"),
               "allowed_hosts": ["testserver", "localhost", "127.0.0.1"], **overrides}
        self.settings = ServerSettings.from_mapping(raw)
        self.getter = RecordingGetter()
        self.assembly = ServerAssembly(self.settings, self.getter)
        self.application = self.assembly.application()
        self.client = TestClient(self.application, follow_redirects=False)

    def session(self, headers: dict[str, str] | None = None, initialize: bool = True) -> McpSession:
        session = McpSession(self.client, headers)
        if initialize:
            assert session.initialize().status_code == 200
        return session

    def set_modes(self, **modes: str) -> None:
        assert self.client.put("/_test/vulns", json=modes).status_code == 200

    def token(self, **body: Any) -> str:
        return str(self.client.post("/_test/mint-token", json=body).json()["access_token"])

    def fetches(self) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = self.client.get("/_test/fetches").json()
        return result

    def requests(self) -> list[dict[str, Any]]:
        result: list[dict[str, Any]] = self.client.get("/_test/requests").json()
        return result


def raw_get(server: TestServer, path: str, query: dict[str, str]) -> tuple[int, dict[str, str]]:
    captured: dict[str, Any] = {}

    async def receive() -> dict[str, Any]:
        return {"type": "http.request", "body": b"", "more_body": False}

    async def send(message: dict[str, Any]) -> None:
        if message["type"] == "http.response.start":
            captured["status"] = message["status"]
            captured["headers"] = {k.decode(): v.decode() for k, v in message["headers"]}

    scope = {"type": "http", "method": "GET", "path": path, "raw_path": path.encode(), "root_path": "",
             "query_string": urlencode(query).encode(), "headers": [(b"host", b"testserver")],
             "scheme": "http", "server": ("testserver", 80), "client": ("127.0.0.1", 1), "http_version": "1.1"}
    anyio.run(server.application, scope, receive, send)
    return int(captured["status"]), dict(captured["headers"])


def pkce_pair() -> tuple[str, str]:
    verifier = secrets.token_urlsafe(32)
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
    return verifier, challenge


def free_port() -> int:
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        return int(probe.getsockname()[1])


@contextmanager
def live_server(tmp_path: Path, **overrides: Any) -> Iterator[str]:
    port = free_port()
    settings = ServerSettings.from_mapping(
        {"host": "127.0.0.1", "port": port, "files_dir": str(tmp_path / "files"), **overrides}
    )
    server = uvicorn.Server(uvicorn.Config(ServerAssembly(settings).application(), host="127.0.0.1", port=port,
                                           log_level="warning"))
    thread = threading.Thread(target=server.run, daemon=True)
    thread.start()
    deadline = time.time() + 10
    while not server.started and time.time() < deadline:
        time.sleep(0.05)
    try:
        yield f"http://127.0.0.1:{port}"
    finally:
        server.should_exit = True
        thread.join(timeout=10)
