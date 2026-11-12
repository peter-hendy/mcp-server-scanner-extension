from __future__ import annotations

import hmac
from enum import StrEnum

from mcp_test_server.oauth.clients import RegisteredClient
from mcp_test_server.weakness import WeaknessSet


class RefreshWeakness(StrEnum):
    MISSING_CLIENT_SECRET = "missing_client_secret"
    WRONG_CLIENT_SECRET = "wrong_client_secret"
    SCOPE_EXPANSION = "scope_expansion"


class RefreshPolicy:
    def __init__(self, weaknesses: WeaknessSet[RefreshWeakness]) -> None:
        self._weaknesses = weaknesses

    def client_authenticated(self, client: RegisteredClient, presented_secret: str | None) -> bool:
        if not client.confidential:
            return True
        if not presented_secret:
            return self._weaknesses.active(RefreshWeakness.MISSING_CLIENT_SECRET)
        if hmac.compare_digest(presented_secret, client.secret or ""):
            return True
        return self._weaknesses.active(RefreshWeakness.WRONG_CLIENT_SECRET)

    def granted_scope(self, original: str, requested: str | None) -> str | None:
        if not requested:
            return original
        if set(requested.split()) <= set(original.split()):
            return requested
        return requested if self._weaknesses.active(RefreshWeakness.SCOPE_EXPANSION) else None
