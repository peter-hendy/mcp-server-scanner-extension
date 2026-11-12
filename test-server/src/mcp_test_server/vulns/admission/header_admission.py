from __future__ import annotations

from abc import abstractmethod
from typing import ClassVar

from starlette.requests import Request
from starlette.responses import JSONResponse, Response

from mcp_test_server.surface import ServerSurface
from mcp_test_server.vulns.base import Vulnerability


class HeaderAdmission(Vulnerability):
    header: ClassVar[str]

    def contribute(self, surface: ServerSurface) -> None:
        self._allowed_hosts = frozenset(host.lower() for host in surface.environment.allowed_hosts)
        surface.admission_gates.append(self)

    def admit(self, request: Request) -> Response | None:
        value = request.headers.get(self.header)
        if self.vulnerable or value is None or self._safely_allowed(value):
            return None
        return JSONResponse({"error": f"invalid {self.header.capitalize()} header"}, status_code=403)

    def _safely_allowed(self, value: str) -> bool:
        try:
            return self.allowed(value)
        except ValueError:
            return False

    def host_allowed(self, host: str | None) -> bool:
        return host is not None and host.lower() in self._allowed_hosts

    @abstractmethod
    def allowed(self, value: str) -> bool: ...
