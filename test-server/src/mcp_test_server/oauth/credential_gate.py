from __future__ import annotations

from enum import StrEnum

from starlette.requests import Request
from starlette.responses import JSONResponse, Response

from mcp_test_server.identity.users import UserDirectory
from mcp_test_server.oauth.token_verifier import InvalidToken, TokenVerifier
from mcp_test_server.transport.routes import SIBLING_MESSAGE_PATH
from mcp_test_server.weakness import WeaknessSet

SESSION_HEADER = "mcp-session-id"
CLIENT_ADDRESS_HEADERS = ("x-forwarded-for", "x-real-ip")
LOOPBACK_ADDRESSES = frozenset({"127.0.0.1", "::1", "localhost"})


class AuthWeakness(StrEnum):
    ANONYMOUS_ACCESS = "anonymous_access"
    FORWARDED_LOOPBACK_TRUST = "forwarded_loopback_trust"
    SESSION_CREDENTIAL = "session_credential"
    PRESENCE_ONLY_CREDENTIAL = "presence_only_credential"
    UNGUARDED_SIBLING_ROUTE = "unguarded_sibling_route"


class CredentialGate:
    def __init__(
        self,
        verifier: TokenVerifier,
        users: UserDirectory,
        weaknesses: WeaknessSet[AuthWeakness],
        resource_metadata_url: str,
    ) -> None:
        self._verifier = verifier
        self._users = users
        self._weaknesses = weaknesses
        self._resource_metadata_url = resource_metadata_url
        self._authenticated_sessions: set[str] = set()

    def admit(self, request: Request) -> Response | None:
        authorization = request.headers.get("authorization")
        if authorization is None:
            return None if self._admits_without_credentials(request) else self._challenge(None)
        scheme, _, token = authorization.partition(" ")
        if scheme.lower() != "bearer" or not token.strip():
            return self._challenge("invalid_request")
        if not self._accepts(token.strip()):
            return self._challenge("invalid_token")
        self._remember_session(request)
        return None

    def _accepts(self, token: str) -> bool:
        if self._users.user_for_token(token) is not None:
            return True
        try:
            self._verifier.verify(token)
        except InvalidToken:
            return self._weaknesses.active(AuthWeakness.PRESENCE_ONLY_CREDENTIAL)
        return True

    def _admits_without_credentials(self, request: Request) -> bool:
        return (
            self._weaknesses.active(AuthWeakness.ANONYMOUS_ACCESS)
            or self._weaknesses.active(AuthWeakness.FORWARDED_LOOPBACK_TRUST) and _forwarded_from_loopback(request)
            or self._weaknesses.active(AuthWeakness.SESSION_CREDENTIAL)
            and request.headers.get(SESSION_HEADER) in self._authenticated_sessions
            or self._weaknesses.active(AuthWeakness.UNGUARDED_SIBLING_ROUTE)
            and request.url.path == SIBLING_MESSAGE_PATH
        )

    def _remember_session(self, request: Request) -> None:
        session_id = request.headers.get(SESSION_HEADER)
        if session_id:
            self._authenticated_sessions.add(session_id)

    def _challenge(self, error: str | None) -> Response:
        parameters = f'resource_metadata="{self._resource_metadata_url}"'
        if error is not None:
            parameters += f', error="{error}"'
        return JSONResponse(
            {"error": error or "unauthorized"},
            status_code=401,
            headers={"WWW-Authenticate": f"Bearer {parameters}"},
        )


def _forwarded_from_loopback(request: Request) -> bool:
    return any(
        _first_hop(request.headers.get(header)) in LOOPBACK_ADDRESSES for header in CLIENT_ADDRESS_HEADERS
    )


def _first_hop(value: str | None) -> str:
    return "" if value is None else value.split(",")[0].strip().lower()
