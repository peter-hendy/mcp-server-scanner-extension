from __future__ import annotations

from pathlib import Path

from mcp_test_server.vulns.traversal.resolvers import PathDenied, PathResolver

_READ_LIMIT = 65536


class FileReader:
    def __init__(self, resolver: PathResolver) -> None:
        self._resolver = resolver

    def read(self, value: str) -> str:
        target = self._resolver.resolve(value)
        if target.is_dir():
            return "\n".join(sorted(entry.name for entry in target.iterdir()))
        if not target.parent.exists():
            raise PathDenied(f"Parent directory does not exist: {target.parent}")
        if not target.exists():
            raise PathDenied(f"ENOENT: no such file or directory, open '{target}'")
        try:
            return _read_text(target)
        except OSError as error:
            raise PathDenied(f"{type(error).__name__}: {error.strerror}") from error


def _read_text(target: Path) -> str:
    with target.open("rb") as handle:
        return handle.read(_READ_LIMIT).decode("utf-8", errors="replace")
