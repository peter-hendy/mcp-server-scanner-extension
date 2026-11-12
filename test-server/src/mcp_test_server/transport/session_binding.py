from __future__ import annotations

from collections.abc import Mapping

from mcp_test_server.identity.users import UserDirectory
from mcp_test_server.transport.sessions import SessionWeakness
from mcp_test_server.weakness import WeaknessSet


class SessionBinding:
    def __init__(self, users: UserDirectory, weaknesses: WeaknessSet[SessionWeakness]) -> None:
        self._users = users
        self._weaknesses = weaknesses

    def owner_for(self, headers: Mapping[str, str]) -> str | None:
        return self._users.name_for(headers)

    def admits(self, session_owner: str | None, headers: Mapping[str, str]) -> bool:
        return self._weaknesses.active(SessionWeakness.UNBOUND_SESSION) or session_owner == self.owner_for(headers)
