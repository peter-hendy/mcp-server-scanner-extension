from __future__ import annotations

import re
from collections.abc import Mapping
from typing import Any

from mcp_test_server.protocol.catalog import Tool
from mcp_test_server.protocol.jsonrpc import HEADER_MISMATCH, JsonObject, JsonRpcError, params_of
from mcp_test_server.transport.routing_headers import decode_header_value

PARAM_HEADER_PREFIX = "mcp-param-"
_ANNOTATION = "x-mcp-header"
_TOKEN = re.compile(r"\A[!#$%&'*+.^_`|~0-9A-Za-z-]+\Z")
_PRIMITIVE_TYPES = frozenset({"string", "integer", "boolean"})

ParamHeaderBindings = dict[str, tuple[str, ...]]


def param_header_bindings(schema: object) -> ParamHeaderBindings:
    bindings: ParamHeaderBindings = {}
    _bind_properties(schema, (), bindings)
    return bindings


class ParamHeaders:
    def __init__(self, tools: Mapping[str, Tool]) -> None:
        self._tools = tools

    def verify(self, message: JsonObject, headers: Mapping[str, str]) -> None:
        if message.get("method") != "tools/call":
            return
        params = params_of(message)
        name = params.get("name")
        tool = self._tools.get(name) if isinstance(name, str) else None
        if tool is None:
            return
        arguments = params.get("arguments")
        for header, path in param_header_bindings(tool.input_schema).items():
            _require_match(header, headers.get(header), _rendered(_value_at(arguments, path)))


def _bind_properties(schema: object, path: tuple[str, ...], bindings: ParamHeaderBindings) -> None:
    properties = schema.get("properties") if isinstance(schema, dict) else None
    if not isinstance(properties, dict):
        return
    for name, property_schema in properties.items():
        property_path = (*path, name)
        header = _annotation(property_schema)
        if header is not None and _is_primitive(property_schema) and header not in bindings:
            bindings[header] = property_path
        _bind_properties(property_schema, property_path, bindings)


def _annotation(property_schema: object) -> str | None:
    annotation = property_schema.get(_ANNOTATION) if isinstance(property_schema, dict) else None
    if isinstance(annotation, str) and _TOKEN.match(annotation):
        return PARAM_HEADER_PREFIX + annotation.lower()
    return None


def _is_primitive(property_schema: dict[str, Any]) -> bool:
    declared = property_schema.get("type")
    types = [declared] if isinstance(declared, str) else declared if isinstance(declared, list) else []
    non_null = [entry for entry in types if entry != "null"]
    return len(non_null) == 1 and non_null[0] in _PRIMITIVE_TYPES


def _value_at(arguments: object, path: tuple[str, ...]) -> object:
    value = arguments
    for name in path:
        if not isinstance(value, dict):
            return None
        value = value.get(name)
    return value


def _rendered(value: object) -> str | None:
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (str, int, float)):
        return str(value)
    return None


def _require_match(header: str, presented: str | None, expected: str | None) -> None:
    decoded = None if presented is None else decode_header_value(presented)
    if decoded != expected:
        raise JsonRpcError(HEADER_MISMATCH,
                           f"Header mismatch: {header} header value '{decoded}' does not match body value '{expected}'")
