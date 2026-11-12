from __future__ import annotations

import json
from collections.abc import Sequence
from typing import Any

from starlette.requests import Request
from starlette.responses import JSONResponse, Response
from starlette.routing import BaseRoute, Route

from mcp_test_server.http.request_log import INSPECTION_PREFIX, RequestLog
from mcp_test_server.modes import Mode, ModeStore
from mcp_test_server.oauth.server_context import DEFAULT_SCOPE, TEST_SUBJECT
from mcp_test_server.oauth.signer import JwtSigner
from mcp_test_server.outbound.fetcher import OutboundFetcher
from mcp_test_server.vulns.base import Vulnerability


class InspectionApi:
    def __init__(
        self,
        request_log: RequestLog,
        fetcher: OutboundFetcher,
        modes: ModeStore,
        vulnerabilities: Sequence[Vulnerability],
        signer: JwtSigner,
        settings_view: dict[str, Any],
    ) -> None:
        self._request_log = request_log
        self._fetcher = fetcher
        self._modes = modes
        self._vulnerabilities = {vulnerability.name: vulnerability for vulnerability in vulnerabilities}
        self._signer = signer
        self._settings_view = settings_view

    def routes(self) -> list[BaseRoute]:
        prefix = INSPECTION_PREFIX
        return [
            Route(f"{prefix}/requests", self._requests, methods=["GET", "DELETE"]),
            Route(f"{prefix}/fetches", self._fetches, methods=["GET", "DELETE"]),
            Route(f"{prefix}/vulns", self._vulns, methods=["GET", "PUT"]),
            Route(f"{prefix}/vulns/{{name}}", self._vuln, methods=["PUT"]),
            Route(f"{prefix}/mint-token", self._mint_token, methods=["POST"]),
            Route(f"{prefix}/settings", self._settings, methods=["GET"]),
        ]

    async def _requests(self, request: Request) -> Response:
        if request.method == "DELETE":
            self._request_log.clear()
            return Response(status_code=204)
        return JSONResponse(self._request_log.entries())

    async def _fetches(self, request: Request) -> Response:
        if request.method == "DELETE":
            self._fetcher.clear()
            return Response(status_code=204)
        await self._fetcher.drain()
        return JSONResponse(self._fetcher.attempts())

    async def _vulns(self, request: Request) -> Response:
        if request.method == "PUT":
            assignments = await _json_object(request)
            rejection = self._apply(assignments)
            if rejection is not None:
                return rejection
        return JSONResponse(self._describe_vulns())

    async def _vuln(self, request: Request) -> Response:
        body = await _json_object(request)
        rejection = self._apply({request.path_params["name"]: body.get("mode")})
        return rejection or JSONResponse(self._describe_vulns())

    def _apply(self, assignments: dict[str, Any]) -> Response | None:
        unknown = set(assignments) - set(self._vulnerabilities)
        if unknown:
            return JSONResponse({"error": f"unknown vulnerabilities: {sorted(unknown)}"}, status_code=404)
        try:
            parsed = {name: Mode(str(mode)) for name, mode in assignments.items()}
        except ValueError:
            return JSONResponse({"error": "mode must be 'vulnerable' or 'safe'"}, status_code=400)
        for name, mode in parsed.items():
            self._modes.set_mode(name, mode)
        return None

    def _describe_vulns(self) -> list[dict[str, str]]:
        return [
            {"name": name, "mode": vulnerability.mode.value, "summary": vulnerability.summary}
            for name, vulnerability in sorted(self._vulnerabilities.items())
        ]

    async def _mint_token(self, request: Request) -> Response:
        body = await _json_object(request)
        token = self._signer.access_token(
            str(body.get("subject", TEST_SUBJECT)),
            str(body.get("scope", DEFAULT_SCOPE)),
            str(body.get("client_id", "public-client")),
            **dict(body.get("claims", {})),
        )
        return JSONResponse({"access_token": token, "token_type": "Bearer"})

    async def _settings(self, request: Request) -> Response:
        return JSONResponse(self._settings_view)


async def _json_object(request: Request) -> dict[str, Any]:
    raw = await request.body()
    if not raw:
        return {}
    decoded = json.loads(raw)
    return decoded if isinstance(decoded, dict) else {}
