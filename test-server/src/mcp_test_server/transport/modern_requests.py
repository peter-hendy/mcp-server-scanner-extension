from __future__ import annotations

from collections.abc import Mapping

from mcp_test_server.protocol.catalog import Tool
from mcp_test_server.protocol.envelope import RequestEnvelope
from mcp_test_server.protocol.jsonrpc import INVALID_PARAMS, JsonObject, JsonRpcError, failure, is_notification
from mcp_test_server.protocol.versions import ProtocolVersions
from mcp_test_server.transport.messages import ParsedBody
from mcp_test_server.transport.param_headers import ParamHeaders
from mcp_test_server.transport.routing_headers import PROTOCOL_VERSION_HEADER, RoutingHeaders

_ENVELOPE_EXEMPT_METHODS = frozenset({"server/discover"})


class ModernRequestGate:
    def __init__(self, versions: ProtocolVersions, tools: Mapping[str, Tool]) -> None:
        self._versions = versions
        self._envelope = RequestEnvelope(versions)
        self._param_headers = ParamHeaders(tools)

    def is_modern(self, body: ParsedBody, headers: Mapping[str, str]) -> bool:
        return self._modern_header(headers) or any(self._claims_modern(message) for message in _requests(body))

    def rejection(self, body: ParsedBody, headers: Mapping[str, str]) -> JsonObject | None:
        for message in _requests(body):
            try:
                self._validate(message, headers)
            except JsonRpcError as error:
                return failure(message.get("id"), error)
        return None

    def _validate(self, message: JsonObject, headers: Mapping[str, str]) -> None:
        if self._claims_modern(message):
            RoutingHeaders(headers).verify(message, self._envelope.protocol_version(message))
            self._param_headers.verify(message, headers)
        elif self._expects_envelope(message, headers):
            raise JsonRpcError(INVALID_PARAMS, "Modern requests require the io.modelcontextprotocol _meta envelope")

    def _claims_modern(self, message: JsonObject) -> bool:
        return not is_notification(message) and self._envelope.claimed_by(message)

    def _expects_envelope(self, message: JsonObject, headers: Mapping[str, str]) -> bool:
        return (
            self._modern_header(headers)
            and not is_notification(message)
            and message.get("method") not in _ENVELOPE_EXEMPT_METHODS
        )

    def _modern_header(self, headers: Mapping[str, str]) -> bool:
        return headers.get(PROTOCOL_VERSION_HEADER) in self._versions.modern()


def _requests(body: ParsedBody) -> list[JsonObject]:
    return [message for message in body.messages if isinstance(message, dict) and "method" in message]
