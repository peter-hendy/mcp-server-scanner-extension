from __future__ import annotations

import base64
import binascii
import hashlib
import hmac
from collections.abc import Mapping
from urllib.parse import unquote

from starlette.requests import Request
from starlette.responses import JSONResponse, Response

from mcp_test_server.oauth.clients import RegisteredClient
from mcp_test_server.oauth.grants import RefreshGrant
from mcp_test_server.oauth.server_context import AuthorizationServerContext
from mcp_test_server.oauth.signer import ACCESS_TOKEN_SECONDS


class TokenError(Exception):
    def __init__(self, error: str, status_code: int = 400) -> None:
        super().__init__(error)
        self.error = error
        self.status_code = status_code


class TokenEndpoint:
    def __init__(self, context: AuthorizationServerContext) -> None:
        self._context = context

    async def handle(self, request: Request) -> Response:
        form = {key: str(value) for key, value in (await request.form()).items()}
        client_id, secret = _client_credentials(request, form)
        try:
            client = self._client(client_id)
            grant_type = form.get("grant_type")
            if grant_type == "authorization_code":
                return await self._exchange_code(client, secret, form)
            if grant_type == "refresh_token":
                return self._refresh(client, secret, form)
            raise TokenError("unsupported_grant_type")
        except TokenError as error:
            return JSONResponse({"error": error.error}, status_code=error.status_code)

    def _client(self, client_id: str | None) -> RegisteredClient:
        client = self._context.clients.get(client_id or "")
        if client is None:
            raise TokenError("invalid_client", 401)
        return client

    async def _exchange_code(self, client: RegisteredClient, secret: str | None, form: Mapping[str, str]) -> Response:
        if client.confidential and not hmac.compare_digest(secret or "", client.secret or ""):
            raise TokenError("invalid_client", 401)
        grant = await self._context.grants.redeem_code(form.get("code", ""))
        if grant is None or grant.client_id != client.client_id:
            raise TokenError("invalid_grant")
        if form.get("redirect_uri", grant.redirect_uri) != grant.redirect_uri:
            raise TokenError("invalid_grant")
        if not _pkce_matches(grant.code_challenge, form.get("code_verifier")):
            raise TokenError("invalid_grant")
        return self._issue(client, RefreshGrant(client.client_id, grant.scope, grant.subject), None)

    def _refresh(self, client: RegisteredClient, secret: str | None, form: Mapping[str, str]) -> Response:
        grant = self._context.grants.refresh_grant(form.get("refresh_token", ""))
        if grant is None or grant.client_id != client.client_id:
            raise TokenError("invalid_grant")
        if not self._context.refresh_policy.client_authenticated(client, secret):
            raise TokenError("invalid_client", 401)
        scope = self._context.refresh_policy.granted_scope(grant.scope, form.get("scope"))
        if scope is None:
            raise TokenError("invalid_scope")
        return self._issue(client, RefreshGrant(client.client_id, scope, grant.subject), form["refresh_token"])

    def _issue(self, client: RegisteredClient, grant: RefreshGrant, refresh_token: str | None) -> Response:
        access_token = self._context.signer.access_token(grant.subject, grant.scope, client.client_id)
        return JSONResponse(
            {
                "access_token": access_token,
                "token_type": "Bearer",
                "expires_in": ACCESS_TOKEN_SECONDS,
                "refresh_token": refresh_token or self._context.grants.issue_refresh_token(grant),
                "scope": grant.scope,
            },
            headers={"Cache-Control": "no-store"},
        )


def _client_credentials(request: Request, form: Mapping[str, str]) -> tuple[str | None, str | None]:
    scheme, _, encoded = request.headers.get("authorization", "").partition(" ")
    if scheme.lower() == "basic":
        try:
            client_id, _, secret = base64.b64decode(encoded).decode().partition(":")
        except (binascii.Error, UnicodeDecodeError):
            return None, None
        return unquote(client_id), unquote(secret)
    return form.get("client_id"), form.get("client_secret")


def _pkce_matches(challenge: str | None, verifier: str | None) -> bool:
    if challenge is None:
        return True
    if verifier is None:
        return False
    digest = hashlib.sha256(verifier.encode()).digest()
    return hmac.compare_digest(base64.urlsafe_b64encode(digest).rstrip(b"=").decode(), challenge)
