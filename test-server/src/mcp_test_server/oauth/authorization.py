from __future__ import annotations

from collections.abc import Mapping
from urllib.parse import urlencode

from starlette.requests import Request
from starlette.responses import HTMLResponse, RedirectResponse, Response

from mcp_test_server.oauth.clients import RegisteredClient
from mcp_test_server.oauth.grants import AuthorizationGrant
from mcp_test_server.oauth.server_context import (
    DEFAULT_SCOPE,
    TEST_SUBJECT,
    AuthorizationServerContext,
    ConsentMode,
)


class AuthorizationRejected(Exception):
    def __init__(self, response: Response) -> None:
        super().__init__("authorization rejected")
        self.response = response


class AuthorizationRequest:
    def __init__(self, context: AuthorizationServerContext, parameters: Mapping[str, str]) -> None:
        self._context = context
        self.parameters = dict(parameters)
        self.client = self._client()
        self.redirect_uri = self._redirect_uri()

    def _client(self) -> RegisteredClient:
        client = self._context.clients.get(self.parameters.get("client_id", ""))
        if client is None:
            raise AuthorizationRejected(_error_page("invalid_client", "Unknown client_id"))
        return client

    def _redirect_uri(self) -> str:
        requested = self.parameters.get("redirect_uri")
        if requested is None and len(self.client.redirect_uris) == 1:
            return self.client.redirect_uris[0]
        if requested is None or not self._context.redirect_policy.accepts(requested, self.client.redirect_uris):
            raise AuthorizationRejected(_error_page("invalid_request", "redirect_uri is not registered"))
        return requested

    def protocol_error(self) -> str | None:
        if self.parameters.get("response_type") != "code":
            return "unsupported_response_type"
        if not self.parameters.get("code_challenge") or self.parameters.get("code_challenge_method") != "S256":
            return "invalid_request"
        return None

    def approve(self) -> Response:
        grant = AuthorizationGrant(
            client_id=self.client.client_id,
            redirect_uri=self.redirect_uri,
            scope=self.parameters.get("scope") or DEFAULT_SCOPE,
            code_challenge=self.parameters.get("code_challenge"),
            subject=TEST_SUBJECT,
        )
        return self.redirect({"code": self._context.grants.issue_code(grant)})

    def redirect(self, values: Mapping[str, str]) -> Response:
        response_values = {**values, "iss": self._context.base_url}
        if "state" in self.parameters:
            response_values["state"] = self.parameters["state"]
        fragment = self.parameters.get("response_mode") == "fragment"
        separator = "#" if fragment else ("&" if "?" in self.redirect_uri else "?")
        return RedirectResponse(f"{self.redirect_uri}{separator}{urlencode(response_values)}", status_code=302)


class AuthorizeEndpoint:
    def __init__(self, context: AuthorizationServerContext) -> None:
        self._context = context

    async def handle(self, request: Request) -> Response:
        try:
            authorization = AuthorizationRequest(self._context, request.query_params)
        except AuthorizationRejected as rejection:
            return rejection.response
        error = authorization.protocol_error()
        if error is not None:
            return authorization.redirect({"error": error})
        if self._context.consent_mode is ConsentMode.AUTO:
            return authorization.approve()
        if authorization.parameters.get("prompt") == "none":
            return authorization.redirect({"error": "consent_required"})
        page = self._context.consent_page.render(authorization.client.client_name, authorization.parameters)
        return HTMLResponse(page)


class ConsentDecisionEndpoint:
    def __init__(self, context: AuthorizationServerContext) -> None:
        self._context = context

    async def handle(self, request: Request) -> Response:
        form = {key: str(value) for key, value in (await request.form()).items()}
        try:
            authorization = AuthorizationRequest(self._context, {**form, "response_type": "code"})
        except AuthorizationRejected as rejection:
            return rejection.response
        if form.get("decision") != "approve":
            return authorization.redirect({"error": "access_denied"})
        return authorization.approve()


def _error_page(error: str, description: str) -> Response:
    return HTMLResponse(f"<h1>{error}</h1><p>{description}</p>", status_code=400)
