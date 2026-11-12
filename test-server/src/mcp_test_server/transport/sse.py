from __future__ import annotations

import asyncio
import json
import secrets
from collections.abc import AsyncIterator

from starlette.requests import Request
from starlette.responses import JSONResponse, Response, StreamingResponse

from mcp_test_server.protocol.dispatcher import Dispatcher
from mcp_test_server.transport.messages import context_for, dispatch_all, parse_body, sse_event

MESSAGES_PATH = "/messages"
_KEEPALIVE_SECONDS = 15.0


class SseChannels:
    def __init__(self) -> None:
        self._queues: dict[str, asyncio.Queue[str]] = {}

    def open(self) -> tuple[str, asyncio.Queue[str]]:
        channel_id = secrets.token_hex(16)
        queue: asyncio.Queue[str] = asyncio.Queue()
        self._queues[channel_id] = queue
        return channel_id, queue

    def get(self, channel_id: str) -> asyncio.Queue[str] | None:
        return self._queues.get(channel_id)

    def close(self, channel_id: str) -> None:
        self._queues.pop(channel_id, None)


class SseStreamEndpoint:
    def __init__(self, channels: SseChannels) -> None:
        self._channels = channels

    async def handle(self, request: Request) -> Response:
        channel_id, queue = self._channels.open()
        return StreamingResponse(self._events(channel_id, queue), media_type="text/event-stream")

    async def _events(self, channel_id: str, queue: asyncio.Queue[str]) -> AsyncIterator[str]:
        try:
            yield sse_event("endpoint", f"{MESSAGES_PATH}?session_id={channel_id}")
            while True:
                try:
                    yield await asyncio.wait_for(queue.get(), timeout=_KEEPALIVE_SECONDS)
                except TimeoutError:
                    yield ": keepalive\n\n"
        finally:
            self._channels.close(channel_id)


class SseMessageEndpoint:
    def __init__(self, dispatcher: Dispatcher, channels: SseChannels) -> None:
        self._dispatcher = dispatcher
        self._channels = channels

    async def handle(self, request: Request) -> Response:
        channel_id = request.query_params.get("session_id", "")
        queue = self._channels.get(channel_id)
        if queue is None:
            return JSONResponse({"error": "Unknown SSE session"}, status_code=404)
        try:
            body = parse_body(await request.body())
        except ValueError:
            return JSONResponse({"error": "Parse error"}, status_code=400)
        for reply in await dispatch_all(self._dispatcher, body, context_for(request, channel_id)):
            await queue.put(sse_event("message", json.dumps(reply)))
        return Response("Accepted", status_code=202)
