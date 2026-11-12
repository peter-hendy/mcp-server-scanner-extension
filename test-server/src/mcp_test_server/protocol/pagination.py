from __future__ import annotations

import base64
import binascii
from collections.abc import Sequence
from typing import Any

from mcp_test_server.protocol.jsonrpc import INVALID_PARAMS, JsonObject, JsonRpcError

_CURSOR_PREFIX = "offset:"


class Paginator:
    def __init__(self, page_size: int) -> None:
        self._page_size = page_size

    def page(self, key: str, items: Sequence[JsonObject], cursor: Any) -> JsonObject:
        start = self._offset(cursor)
        end = start + self._page_size
        result: JsonObject = {key: list(items[start:end])}
        if end < len(items):
            result["nextCursor"] = self.encode(end)
        return result

    @staticmethod
    def encode(offset: int) -> str:
        return base64.urlsafe_b64encode(f"{_CURSOR_PREFIX}{offset}".encode()).decode()

    def _offset(self, cursor: Any) -> int:
        if cursor is None:
            return 0
        if not isinstance(cursor, str):
            raise JsonRpcError(INVALID_PARAMS, "Invalid cursor")
        try:
            decoded = base64.urlsafe_b64decode(cursor.encode()).decode()
        except (binascii.Error, UnicodeDecodeError, ValueError) as error:
            raise JsonRpcError(INVALID_PARAMS, "Invalid cursor") from error
        if not decoded.startswith(_CURSOR_PREFIX) or not decoded[len(_CURSOR_PREFIX):].isdigit():
            raise JsonRpcError(INVALID_PARAMS, "Invalid cursor")
        return int(decoded[len(_CURSOR_PREFIX):])
