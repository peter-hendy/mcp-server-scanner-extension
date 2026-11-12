from __future__ import annotations

import json
from collections.abc import Mapping
from enum import StrEnum

from starlette.requests import Request
from starlette.responses import JSONResponse, Response, StreamingResponse

from mcp_test_server.protocol.catalog import Tool
from mcp_test_server.protocol.dispatcher import Dispatcher
from mcp_test_server.protocol.jsonrpc import JsonObject
from mcp_test_server.protocol.versions import ProtocolVersions
from mcp_test_server.transport.messages import (
    ParsedBody,
    context_for,
    dispatch_all,
    parse_body,
    parse_error_reply,
    sse_event,
)
from mcp_test_server.transport.modern_requests import ModernRequestGate
from mcp_test_server.transport.routing_headers import PROTOCOL_VERSION_HEADER
from mcp_test_server.transport.session_binding import SessionBinding
from mcp_test_server.transport.sessions import SESSION_HEADER, SessionPolicy, SessionStore

_SESSIONLESS_METHODS = frozenset({"initialize", "server/discover"})


class ResponseMode(StrEnum):
    JSON = "json"
    SSE = "sse"


class StreamableHttpEndpoint:
    def __init__(
        self,
        dispatcher: Dispatcher,
        sessions: SessionStore,
        binding: SessionBinding,
        response_mode: ResponseMode,
        versions: ProtocolVersions,
        tools: Mapping[str, Tool],
    ) -> None:
        self._dispatcher = dispatcher
        self._sessions = sessions
        self._binding = binding
        self._response_mode = response_mode
        self._versions = versions
        self._modern_gate = ModernRequestGate(versions, tools)

    async def handle(self, request: Request) -> Response:
        if request.method == "DELETE":
            return self._delete(request)
        if request.method != "POST":
            return Response(status_code=405, headers={"Allow": "POST, DELETE"})
        return await self._post(request)

    async def _post(self, request: Request) -> Response:
        try:
            body = parse_body(await request.body())
        except ValueError:
            return JSONResponse(parse_error_reply(), status_code=400)
        version = request.headers.get(PROTOCOL_VERSION_HEADER)
        if version is not None and not self._opens_session(body) and not self._versions.supports(version):
            return JSONResponse({"error": f"Unsupported MCP-Protocol-Version: {version}"}, status_code=400)
        modern_rejection = self._modern_gate.rejection(body, request.headers)
        if modern_rejection is not None:
            return JSONResponse(modern_rejection, status_code=400)
        session_id, rejection = self._session_for(request, body)
        if rejection is not None:
            return rejection
        context = context_for(request, session_id, self._sessions.owner_of(session_id))
        replies = await dispatch_all(self._dispatcher, body, context)
        return self._render(replies, body.batch, session_id)

    def _session_for(self, request: Request, body: ParsedBody) -> tuple[str | None, Response | None]:
        if self._modern_gate.is_modern(body, request.headers):
            return None, None
        return self._resolve_session(request, body)

    def _resolve_session(self, request: Request, body: ParsedBody) -> tuple[str | None, Response | None]:
        if self._opens_session(body) and self._dispatcher.supports("initialize") and self._versions.handshake():
            return self._sessions.open(self._binding.owner_for(request.headers)), None
        presented = request.headers.get(SESSION_HEADER)
        if presented is not None and not self._sessions.exists(presented):
            if self._sessions.policy is SessionPolicy.OFF:
                return None, None
            return None, _unknown_session()
        if presented is not None and not self._binding.admits(self._sessions.owner_of(presented), request.headers):
            return None, _unknown_session()
        if presented is None and self._requires_session(body):
            return None, JSONResponse({"error": "Missing Mcp-Session-Id header"}, status_code=400)
        return presented, None

    @staticmethod
    def _opens_session(body: ParsedBody) -> bool:
        return any(isinstance(m, dict) and m.get("method") == "initialize" for m in body.messages)

    def _requires_session(self, body: ParsedBody) -> bool:
        if self._sessions.policy is not SessionPolicy.REQUIRED:
            return False
        return any(isinstance(m, dict) and m.get("method") not in _SESSIONLESS_METHODS for m in body.messages)

    def _render(self, replies: list[JsonObject], batch: bool, session_id: str | None) -> Response:
        headers = {SESSION_HEADER: session_id} if session_id else {}
        if not replies:
            return Response(status_code=202, headers=headers)
        payload: object = replies if batch else replies[0]
        if self._response_mode is ResponseMode.SSE:
            event = sse_event("message", json.dumps(payload))
            return StreamingResponse(iter([event]), media_type="text/event-stream", headers=headers)
        return JSONResponse(payload, headers=headers)

    def _delete(self, request: Request) -> Response:
        session_id = request.headers.get(SESSION_HEADER)
        if session_id is None:
            return Response(status_code=400)
        return Response(status_code=200 if self._sessions.close(session_id) else 404)


def _unknown_session() -> Response:
    return JSONResponse({"error": "Unknown session"}, status_code=404)
