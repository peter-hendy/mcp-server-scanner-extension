from __future__ import annotations

import base64
import binascii
import re
from collections.abc import Mapping

from mcp_test_server.protocol.jsonrpc import HEADER_MISMATCH, JsonObject, JsonRpcError, params_of

PROTOCOL_VERSION_HEADER = "mcp-protocol-version"
METHOD_HEADER = "mcp-method"
NAME_HEADER = "mcp-name"
NAME_BEARING_METHODS = frozenset({"tools/call", "resources/read", "prompts/get"})
_BASE64_SENTINEL = re.compile(r"\A=\?base64\?(.*)\?=\Z", re.DOTALL)


def decode_header_value(value: str) -> str:
    match = _BASE64_SENTINEL.match(value)
    if match is None:
        return value
    try:
        return base64.b64decode(match.group(1), validate=True).decode("utf-8")
    except (binascii.Error, UnicodeDecodeError):
        return value


class RoutingHeaders:
    def __init__(self, headers: Mapping[str, str]) -> None:
        self._headers = headers

    def verify(self, message: JsonObject, protocol_version: str) -> None:
        self._require_match(PROTOCOL_VERSION_HEADER, self._presented(PROTOCOL_VERSION_HEADER), protocol_version)
        self._require_match(METHOD_HEADER, self._presented(METHOD_HEADER), message.get("method"))
        name = self._routed_name(message)
        if name is not None:
            self._require_match(NAME_HEADER, decode_header_value(self._presented(NAME_HEADER)), name)

    def _presented(self, header: str) -> str:
        value = self._headers.get(header)
        if not value:
            raise _mismatch(f"{header} header is required")
        return value

    @staticmethod
    def _require_match(header: str, presented: str, expected: object) -> None:
        if presented != expected:
            raise _mismatch(f"{header} header value '{presented}' does not match body value '{expected}'")

    @staticmethod
    def _routed_name(message: JsonObject) -> str | None:
        if message.get("method") not in NAME_BEARING_METHODS:
            return None
        params = params_of(message)
        name = params.get("name", params.get("uri"))
        return name if isinstance(name, str) else None


def _mismatch(detail: str) -> JsonRpcError:
    return JsonRpcError(HEADER_MISMATCH, f"Header mismatch: {detail}")
