from __future__ import annotations

from collections.abc import Callable
from pathlib import Path
from typing import Any

import pytest

from tests.support import TestServer

ServerFactory = Callable[..., TestServer]


@pytest.fixture
def make_server(tmp_path: Path) -> ServerFactory:
    def factory(**overrides: Any) -> TestServer:
        return TestServer(tmp_path, **overrides)

    return factory


@pytest.fixture
def server(make_server: ServerFactory) -> TestServer:
    return make_server()
