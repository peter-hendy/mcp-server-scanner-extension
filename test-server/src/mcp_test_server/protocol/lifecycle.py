from __future__ import annotations

from dataclasses import dataclass

from mcp_test_server.protocol.capabilities import CapabilityAdvertiser
from mcp_test_server.protocol.context import CallContext
from mcp_test_server.protocol.families import Family
from mcp_test_server.protocol.identity import ServerIdentity
from mcp_test_server.protocol.jsonrpc import JsonObject
from mcp_test_server.protocol.versions import ProtocolVersions


@dataclass(frozen=True)
class InitializeHandler:
    identity: ServerIdentity
    advertiser: CapabilityAdvertiser
    versions: ProtocolVersions
    method: str = "initialize"
    family: Family = Family.INITIALIZE

    async def handle(self, params: JsonObject, context: CallContext) -> JsonObject:
        return {
            "protocolVersion": self.versions.negotiate_handshake(params.get("protocolVersion")),
            "capabilities": self.advertiser.capabilities(),
            "serverInfo": self.identity.server_info(),
            "instructions": self.identity.instructions,
        }


@dataclass(frozen=True)
class ServerDiscoverHandler:
    identity: ServerIdentity
    advertiser: CapabilityAdvertiser
    versions: ProtocolVersions
    method: str = "server/discover"
    family: Family = Family.SERVER_DISCOVER

    async def handle(self, params: JsonObject, context: CallContext) -> JsonObject:
        return {
            "supportedVersions": list(self.versions.modern()),
            "capabilities": self.advertiser.capabilities(),
            "serverInfo": self.identity.server_info(),
            "instructions": self.identity.instructions,
            "ttlMs": 0,
            "cacheScope": "private",
            "resultType": "complete",
        }


@dataclass(frozen=True)
class EmptyResultHandler:
    method: str
    family: Family

    async def handle(self, params: JsonObject, context: CallContext) -> JsonObject:
        return {}


@dataclass(frozen=True)
class NotificationHandler:
    method: str
    family: Family

    async def handle(self, params: JsonObject, context: CallContext) -> None:
        return None
