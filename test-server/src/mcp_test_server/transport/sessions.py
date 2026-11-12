from __future__ import annotations

import itertools
import secrets
import threading
from enum import StrEnum

from mcp_test_server.weakness import WeaknessSet

SESSION_HEADER = "mcp-session-id"


class SessionPolicy(StrEnum):
    REQUIRED = "required"
    OPTIONAL = "optional"
    OFF = "off"


class SessionWeakness(StrEnum):
    UNBOUND_SESSION = "unbound_session"
    GUESSABLE_IDS = "guessable_ids"
    DELETE_IGNORED = "delete_ignored"


class SessionStore:
    def __init__(self, policy: SessionPolicy, weaknesses: WeaknessSet[SessionWeakness] | None = None) -> None:
        self.policy = policy
        self._weaknesses = weaknesses or WeaknessSet()
        self._owners: dict[str, str | None] = {}
        self._counter = itertools.count(1)
        self._lock = threading.Lock()

    def open(self, owner: str | None = None) -> str | None:
        if self.policy is SessionPolicy.OFF:
            return None
        with self._lock:
            session_id = self._new_id()
            self._owners[session_id] = owner
        return session_id

    def exists(self, session_id: str) -> bool:
        with self._lock:
            return session_id in self._owners

    def owner_of(self, session_id: str | None) -> str | None:
        with self._lock:
            return self._owners.get(session_id) if session_id else None

    def close(self, session_id: str) -> bool:
        with self._lock:
            if session_id not in self._owners:
                return False
            if not self._weaknesses.active(SessionWeakness.DELETE_IGNORED):
                del self._owners[session_id]
            return True

    def _new_id(self) -> str:
        if self._weaknesses.active(SessionWeakness.GUESSABLE_IDS):
            return f"session-{next(self._counter):06d}"
        return secrets.token_hex(16)
