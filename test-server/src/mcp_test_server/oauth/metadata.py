from __future__ import annotations

from starlette.requests import Request
from starlette.responses import JSONResponse, Response

from mcp_test_server.oauth.server_context import SUPPORTED_SCOPES, AuthorizationServerContext


class AuthorizationServerMetadata:
    def __init__(self, context: AuthorizationServerContext) -> None:
        self._context = context

    async def handle(self, request: Request) -> Response:
        base = self._context.base_url
        return JSONResponse(
            {
                "issuer": base,
                "authorization_endpoint": f"{base}/authorize",
                "token_endpoint": f"{base}/token",
                "registration_endpoint": f"{base}/register",
                "jwks_uri": f"{base}/jwks.json",
                "scopes_supported": list(SUPPORTED_SCOPES),
                "response_types_supported": ["code"],
                "response_modes_supported": ["query", "fragment"],
                "grant_types_supported": ["authorization_code", "refresh_token"],
                "code_challenge_methods_supported": ["S256"],
                "token_endpoint_auth_methods_supported": ["none", "client_secret_basic", "client_secret_post"],
                "authorization_response_iss_parameter_supported": True,
            }
        )


class ProtectedResourceMetadata:
    def __init__(self, context: AuthorizationServerContext) -> None:
        self._context = context

    async def handle(self, request: Request) -> Response:
        return JSONResponse(
            {
                "resource": self._context.signer.audience,
                "authorization_servers": [self._context.base_url],
                "scopes_supported": list(SUPPORTED_SCOPES),
                "bearer_methods_supported": ["header"],
            }
        )


class JwksEndpoint:
    def __init__(self, context: AuthorizationServerContext) -> None:
        self._context = context

    async def handle(self, request: Request) -> Response:
        return JSONResponse(self._context.signer.jwks())


class PublicKeyEndpoint:
    def __init__(self, context: AuthorizationServerContext) -> None:
        self._context = context

    async def handle(self, request: Request) -> Response:
        return Response(self._context.signer.public_key_pem(), media_type="application/x-pem-file")
