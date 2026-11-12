from __future__ import annotations

import posixpath
from urllib.parse import quote, unquote

from starlette.requests import Request
from starlette.responses import Response
from starlette.routing import Route

from mcp_test_server.protocol.catalog import Arguments, Tool, text_content
from mcp_test_server.protocol.context import CallContext
from mcp_test_server.protocol.jsonrpc import JsonObject
from mcp_test_server.surface import ServerSurface
from mcp_test_server.vulns.base import Vulnerability
from mcp_test_server.vulns.downstream.downstream_api import DownstreamApi

DOWNSTREAM_PREFIX = "/downstream"
LICENSE_PATH = "/api/v1/licenses/"


class DownstreamRouteEscape(Vulnerability):
    name = "downstream_route_escape"
    summary = "get_license builds a same-host downstream API path from license_id without encoding it"

    def contribute(self, surface: ServerSurface) -> None:
        self._api = DownstreamApi()
        schema = {
            "type": "object",
            "properties": {"license_id": {"type": "string", "default": "LIC-1001"}},
            "required": ["license_id"],
        }
        surface.catalog.add_tool(Tool("get_license", "Look up a license in the licensing service", schema,
                                      self._call))
        surface.routes.append(Route(DOWNSTREAM_PREFIX + "/{path:path}", self._serve_http))

    def downstream_path(self, license_id: str) -> str:
        if self.vulnerable:
            return posixpath.normpath(LICENSE_PATH + unquote(license_id))
        return LICENSE_PATH + quote(license_id, safe="")

    async def _call(self, arguments: Arguments, context: CallContext) -> JsonObject:
        response = self._api.get(self.downstream_path(str(arguments.get("license_id", ""))))
        return text_content(response.body)

    async def _serve_http(self, request: Request) -> Response:
        response = self._api.get("/" + request.path_params["path"])
        return Response(response.body, status_code=response.status, media_type="application/json")
