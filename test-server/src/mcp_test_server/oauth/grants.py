from __future__ import annotations

import asyncio
import secrets
import threading
from dataclasses import dataclass
from enum import StrEnum

from mcp_test_server.weakness import WeaknessSet

REDEMPTION_RACE_WINDOW_SECONDS = 0.2


class CodeWeakness(StrEnum):
    REUSE = "reuse"
    REDEMPTION_RACE = "redemption_race"


@dataclass(frozen=True)
class AuthorizationGrant:
    client_id: str
    redirect_uri: str
    scope: str
    code_challenge: str | None
    subject: str


@dataclass(frozen=True)
class RefreshGrant:
    client_id: str
    scope: str
    subject: str


class GrantStore:
    def __init__(self, code_weaknesses: WeaknessSet[CodeWeakness] | None = None) -> None:
        self._codes: dict[str, AuthorizationGrant] = {}
        self._refresh_tokens: dict[str, RefreshGrant] = {}
        self._lock = threading.Lock()
        self._code_weaknesses = code_weaknesses or WeaknessSet()

    def issue_code(self, grant: AuthorizationGrant) -> str:
        code = secrets.token_urlsafe(24)
        with self._lock:
            self._codes[code] = grant
        return code

    async def redeem_code(self, code: str) -> AuthorizationGrant | None:
        if self._code_weaknesses.active(CodeWeakness.REUSE):
            return self._find(code)
        if self._code_weaknesses.active(CodeWeakness.REDEMPTION_RACE):
            return await self._find_then_delete(code)
        with self._lock:
            return self._codes.pop(code, None)

    def issue_refresh_token(self, grant: RefreshGrant) -> str:
        token = secrets.token_urlsafe(32)
        with self._lock:
            self._refresh_tokens[token] = grant
        return token

    def refresh_grant(self, token: str) -> RefreshGrant | None:
        with self._lock:
            return self._refresh_tokens.get(token)

    def _find(self, code: str) -> AuthorizationGrant | None:
        with self._lock:
            return self._codes.get(code)

    async def _find_then_delete(self, code: str) -> AuthorizationGrant | None:
        grant = self._find(code)
        await asyncio.sleep(REDEMPTION_RACE_WINDOW_SECONDS)
        with self._lock:
            self._codes.pop(code, None)
        return grant
