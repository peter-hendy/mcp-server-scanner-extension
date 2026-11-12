from __future__ import annotations

from typing import ClassVar

from mcp_test_server.surface import ServerSurface
from mcp_test_server.vulns.base import Vulnerability


class ConsentReflection(Vulnerability):
    field: ClassVar[str]

    def contribute(self, surface: ServerSurface) -> None:
        surface.consent_reflections.register(self.field, self)
