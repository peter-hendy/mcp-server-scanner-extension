from __future__ import annotations

import importlib
import inspect
import pkgutil

from mcp_test_server import vulns
from mcp_test_server.modes import ModeStore
from mcp_test_server.vulns.base import Vulnerability


def vulnerability_types() -> list[type[Vulnerability]]:
    for module in pkgutil.walk_packages(vulns.__path__, f"{vulns.__name__}."):
        importlib.import_module(module.name)
    found = {cls.name: cls for cls in _descendants(Vulnerability) if _is_registrable(cls)}
    return [found[name] for name in sorted(found)]


def vulnerability_names() -> set[str]:
    return {cls.name for cls in vulnerability_types()}


def instantiate(modes: ModeStore) -> list[Vulnerability]:
    return [cls(modes) for cls in vulnerability_types()]


def _is_registrable(cls: type[Vulnerability]) -> bool:
    return not inspect.isabstract(cls) and "name" in vars(cls)


def _descendants(base: type) -> list[type[Vulnerability]]:
    direct: list[type[Vulnerability]] = base.__subclasses__()
    return direct + [nested for cls in direct for nested in _descendants(cls)]
