from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass


@dataclass(frozen=True)
class CallContext:
    headers: Mapping[str, str]
    session_id: str | None = None
    session_owner: str | None = None

    def header(self, name: str) -> str | None:
        return self.headers.get(name.lower())
