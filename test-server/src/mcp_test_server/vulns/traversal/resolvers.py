from __future__ import annotations

import os
from pathlib import Path
from typing import Protocol
from urllib.parse import unquote


class PathDenied(Exception):
    pass


class PathResolver(Protocol):
    def resolve(self, value: str) -> Path: ...


def _denied(value: str, root: Path) -> PathDenied:
    return PathDenied(f"Access denied - path outside allowed directories: {value} not in {root}")


def _strip_file_scheme(value: str) -> str:
    return value[len("file://"):] if value.startswith("file://") else value


class ContainedResolver:
    def __init__(self, root: Path) -> None:
        self._root = root

    def resolve(self, value: str) -> Path:
        candidate = Path(os.path.realpath(os.path.join(self._root, _strip_file_scheme(value))))
        if os.path.commonpath([self._root, candidate]) != str(self._root):
            raise _denied(value, self._root)
        return candidate


class UnrestrictedResolver:
    def __init__(self, root: Path) -> None:
        self._root = root

    def resolve(self, value: str) -> Path:
        return Path(os.path.normpath(os.path.join(self._root, _strip_file_scheme(value))))


class DecodeAfterCheckResolver:
    def __init__(self, root: Path, decoded_resolver: PathResolver) -> None:
        self._root = root
        self._decoded_resolver = decoded_resolver

    def resolve(self, value: str) -> Path:
        stripped = _strip_file_scheme(value)
        if "../" in stripped or "..\\" in stripped or stripped.startswith("/"):
            raise _denied(value, self._root)
        return self._decoded_resolver.resolve(unquote(stripped))


class PrefixMatchResolver:
    def __init__(self, root: Path) -> None:
        self._root = root

    def resolve(self, value: str) -> Path:
        candidate = os.path.normpath(os.path.join(self._root, _strip_file_scheme(value)))
        if not candidate.startswith(str(self._root)):
            raise _denied(value, self._root)
        return Path(candidate)
