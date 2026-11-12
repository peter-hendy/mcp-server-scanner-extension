from __future__ import annotations

from collections.abc import Mapping

from starlette.routing import BaseRoute, Route

from mcp_test_server.protocol.catalog import Tool
from mcp_test_server.protocol.dispatcher import Dispatcher
from mcp_test_server.protocol.families import Family, FamilySwitches
from mcp_test_server.protocol.versions import ProtocolVersions
from mcp_test_server.transport.session_binding import SessionBinding
from mcp_test_server.transport.sessions import SessionStore
from mcp_test_server.transport.sse import MESSAGES_PATH, SseChannels, SseMessageEndpoint, SseStreamEndpoint
from mcp_test_server.transport.streamable_http import ResponseMode, StreamableHttpEndpoint

STREAMABLE_HTTP_PATH = "/mcp"
SIBLING_MESSAGE_PATH = "/message"
SSE_PATH = "/sse"
GUARDED_PATHS = (STREAMABLE_HTTP_PATH, SIBLING_MESSAGE_PATH, SSE_PATH, MESSAGES_PATH)


def transport_routes(
    dispatcher: Dispatcher,
    sessions: SessionStore,
    binding: SessionBinding,
    response_mode: ResponseMode,
    families: FamilySwitches,
    versions: ProtocolVersions,
    tools: Mapping[str, Tool],
) -> list[BaseRoute]:
    routes: list[BaseRoute] = []
    if families.enabled(Family.STREAMABLE_HTTP):
        endpoint = StreamableHttpEndpoint(dispatcher, sessions, binding, response_mode, versions, tools)
        routes.append(Route(STREAMABLE_HTTP_PATH, endpoint.handle, methods=["GET", "POST", "DELETE"]))
        routes.append(Route(SIBLING_MESSAGE_PATH, endpoint.handle, methods=["GET", "POST", "DELETE"]))
    if families.enabled(Family.SSE):
        channels = SseChannels()
        routes.append(Route(SSE_PATH, SseStreamEndpoint(channels).handle, methods=["GET"]))
        routes.append(Route(MESSAGES_PATH, SseMessageEndpoint(dispatcher, channels).handle, methods=["POST"]))
    return routes
