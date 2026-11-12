from __future__ import annotations

from collections.abc import Hashable
from typing import Generic, Protocol, TypeVar

Key = TypeVar("Key", bound=Hashable)


class Switchable(Protocol):
    @property
    def vulnerable(self) -> bool: ...


class WeaknessSet(Generic[Key]):
    def __init__(self) -> None:
        self._switches: dict[Key, Switchable] = {}

    def register(self, key: Key, switch: Switchable) -> None:
        self._switches[key] = switch

    def active(self, key: Key) -> bool:
        switch = self._switches.get(key)
        return switch is not None and switch.vulnerable
