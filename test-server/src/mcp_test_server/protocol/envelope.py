from __future__ import annotations

from mcp_test_server.protocol.jsonrpc import INVALID_PARAMS, JsonObject, JsonRpcError, params_of
from mcp_test_server.protocol.versions import ProtocolVersions

PROTOCOL_VERSION_META_KEY = "io.modelcontextprotocol/protocolVersion"
CLIENT_CAPABILITIES_META_KEY = "io.modelcontextprotocol/clientCapabilities"
CLIENT_INFO_META_KEY = "io.modelcontextprotocol/clientInfo"


class RequestEnvelope:
    def __init__(self, versions: ProtocolVersions) -> None:
        self._versions = versions

    @staticmethod
    def claimed_by(message: JsonObject) -> bool:
        return PROTOCOL_VERSION_META_KEY in _meta_of(message)

    def protocol_version(self, message: JsonObject) -> str:
        meta = _meta_of(message)
        invalid = self._invalid_keys(meta)
        if invalid:
            raise JsonRpcError(INVALID_PARAMS, f"Invalid params: missing or invalid {', '.join(invalid)} in _meta")
        return self._versions.require_modern(meta[PROTOCOL_VERSION_META_KEY])

    @staticmethod
    def _invalid_keys(meta: JsonObject) -> list[str]:
        invalid = []
        if not isinstance(meta.get(PROTOCOL_VERSION_META_KEY), str):
            invalid.append(PROTOCOL_VERSION_META_KEY)
        if not isinstance(meta.get(CLIENT_CAPABILITIES_META_KEY), dict):
            invalid.append(CLIENT_CAPABILITIES_META_KEY)
        if not isinstance(meta.get(CLIENT_INFO_META_KEY, {}), dict):
            invalid.append(CLIENT_INFO_META_KEY)
        return invalid


def _meta_of(message: JsonObject) -> JsonObject:
    meta = params_of(message).get("_meta")
    return meta if isinstance(meta, dict) else {}
