from __future__ import annotations

from collections.abc import Iterator
from typing import Any


def iter_leaves(value: Any, path: str) -> Iterator[tuple[str, Any]]:
    if isinstance(value, dict):
        if not value:
            yield path, value
        for key, child in value.items():
            yield from iter_leaves(child, f"{path}.{key}")
    elif isinstance(value, list):
        if not value:
            yield path, value
        for index, child in enumerate(value):
            yield from iter_leaves(child, f"{path}[{index}]")
    else:
        yield path, value


def leaf_paths(value: Any, path: str) -> list[str]:
    return [leaf_path for leaf_path, _ in iter_leaves(value, path)]
