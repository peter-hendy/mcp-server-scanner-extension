from __future__ import annotations

import threading
from collections.abc import Mapping
from enum import StrEnum


class Mode(StrEnum):
    VULNERABLE = "vulnerable"
    SAFE = "safe"


class ModeStore:
    def __init__(self, default: Mode, overrides: Mapping[str, Mode]) -> None:
        self._default = default
        self._modes: dict[str, Mode] = dict(overrides)
        self._lock = threading.Lock()

    def mode_of(self, name: str) -> Mode:
        with self._lock:
            return self._modes.get(name, self._default)

    def set_mode(self, name: str, mode: Mode) -> None:
        with self._lock:
            self._modes[name] = mode
