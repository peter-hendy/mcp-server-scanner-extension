from __future__ import annotations

import tempfile
from dataclasses import dataclass
from pathlib import Path

WORKSPACE_FILES = {
    "readme.txt": "Workspace readme: quarterly planning notes.\n",
    "notes/todo.txt": "1. Review access logs\n2. Rotate keys\n",
}
OUTSIDE_SECRET = "OUTSIDE-ROOT-SECRET-7d1f2c\n"


@dataclass(frozen=True)
class FileSandbox:
    base: Path

    @property
    def root(self) -> Path:
        return self.base / "workspace"

    @property
    def outside_secret(self) -> Path:
        return self.base / "outside-secret.txt"

    @classmethod
    def create(cls, base: Path | None) -> FileSandbox:
        resolved = (base or Path(tempfile.mkdtemp(prefix="mcp-test-files-"))).resolve()
        sandbox = cls(resolved)
        sandbox._populate()
        return sandbox

    def _populate(self) -> None:
        for relative, text in WORKSPACE_FILES.items():
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text, encoding="utf-8")
        self.outside_secret.write_text(OUTSIDE_SECRET, encoding="utf-8")
