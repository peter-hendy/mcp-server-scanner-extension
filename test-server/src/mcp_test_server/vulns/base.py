from __future__ import annotations

from abc import ABC, abstractmethod
from typing import ClassVar

from mcp_test_server.modes import Mode, ModeStore
from mcp_test_server.surface import ServerSurface


class Vulnerability(ABC):
    name: ClassVar[str]
    summary: ClassVar[str]

    def __init__(self, modes: ModeStore) -> None:
        self._modes = modes

    @property
    def mode(self) -> Mode:
        return self._modes.mode_of(self.name)

    @property
    def vulnerable(self) -> bool:
        return self.mode is Mode.VULNERABLE

    @abstractmethod
    def contribute(self, surface: ServerSurface) -> None: ...
