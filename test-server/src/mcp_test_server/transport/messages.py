from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any

from starlette.requests import Request

from mcp_test_server.protocol.context import CallContext
from mcp_test_server.protocol.dispatcher import Dispatcher
from mcp_test_server.protocol.jsonrpc import PARSE_ERROR, JsonObject, JsonRpcError, failure


@dataclass(frozen=True)
class ParsedBody:
    messages: list[Any]
    batch: bool


def parse_body(raw: bytes) -> ParsedBody:
    decoded = json.loads(raw)
    if decoded == []:
        return ParsedBody([None], batch=False)
    if isinstance(decoded, list):
        return ParsedBody(decoded, batch=True)
    return ParsedBody([decoded], batch=False)


def parse_error_reply() -> JsonObject:
    return failure(None, JsonRpcError(PARSE_ERROR, "Parse error"))


def context_for(request: Request, session_id: str | None, session_owner: str | None = None) -> CallContext:
    return CallContext(headers={k.lower(): v for k, v in request.headers.items()}, session_id=session_id,
                       session_owner=session_owner)


async def dispatch_all(dispatcher: Dispatcher, body: ParsedBody, context: CallContext) -> list[JsonObject]:
    replies = [await dispatcher.dispatch(message, context) for message in body.messages]
    return [reply for reply in replies if reply is not None]


def sse_event(event: str, data: str) -> str:
    return f"event: {event}\ndata: {data}\n\n"
