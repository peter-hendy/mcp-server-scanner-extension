from __future__ import annotations

import json
from typing import Any

from starlette.requests import Request
from starlette.responses import JSONResponse, Response

from mcp_test_server.oauth.server_context import AuthorizationServerContext


class RegistrationEndpoint:
    def __init__(self, context: AuthorizationServerContext) -> None:
        self._context = context

    async def handle(self, request: Request) -> Response:
        try:
            metadata = json.loads(await request.body())
        except ValueError:
            return _invalid("invalid_client_metadata", "Body must be JSON")
        if not isinstance(metadata, dict):
            return _invalid("invalid_client_metadata", "Body must be a JSON object")
        redirect_uris = metadata.get("redirect_uris")
        if not isinstance(redirect_uris, list) or not redirect_uris:
            return _invalid("invalid_redirect_uri", "redirect_uris is required")
        for uri in redirect_uris:
            problem = self._context.redirect_policy.registration_error(str(uri))
            if problem is not None:
                return _invalid("invalid_redirect_uri", f"{uri}: {problem}")
        self._notify(metadata)
        client = self._context.clients.register(metadata, [str(uri) for uri in redirect_uris])
        return JSONResponse(client.describe(), status_code=201)

    def _notify(self, metadata: dict[str, Any]) -> None:
        for observer in self._context.registration_observers:
            observer.on_register(metadata)


def _invalid(error: str, description: str) -> Response:
    return JSONResponse({"error": error, "error_description": description}, status_code=400)
