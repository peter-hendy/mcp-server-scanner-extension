from __future__ import annotations

from collections.abc import Iterable
from dataclasses import dataclass

from mcp_test_server.protocol.jsonrpc import INVALID_PARAMS, JsonRpcError

MODERN_PROTOCOL_VERSIONS = ("2026-07-28",)
HANDSHAKE_PROTOCOL_VERSIONS = ("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05")
KNOWN_PROTOCOL_VERSIONS = MODERN_PROTOCOL_VERSIONS + HANDSHAKE_PROTOCOL_VERSIONS


def parse_protocol_versions(values: Iterable[object]) -> tuple[str, ...]:
    versions = tuple(dict.fromkeys(str(value).strip() for value in values))
    unknown = [version for version in versions if version not in KNOWN_PROTOCOL_VERSIONS]
    if unknown:
        raise ValueError(f"Unknown protocol versions: {', '.join(unknown)}")
    if not versions:
        raise ValueError("At least one protocol version must be enabled")
    return versions


@dataclass(frozen=True)
class ProtocolVersions:
    enabled: tuple[str, ...] = KNOWN_PROTOCOL_VERSIONS

    def supports(self, version: str) -> bool:
        return version in self.enabled

    def modern(self) -> tuple[str, ...]:
        return self._enabled_among(MODERN_PROTOCOL_VERSIONS)

    def handshake(self) -> tuple[str, ...]:
        return self._enabled_among(HANDSHAKE_PROTOCOL_VERSIONS)

    def negotiate_handshake(self, requested: object) -> str:
        handshake = self.handshake()
        if not handshake:
            raise _unsupported(requested, self.enabled)
        return requested if isinstance(requested, str) and requested in handshake else handshake[0]

    def require_modern(self, requested: object) -> str:
        if isinstance(requested, str) and requested in self.modern():
            return requested
        raise _unsupported(requested, self.modern())

    def _enabled_among(self, era: tuple[str, ...]) -> tuple[str, ...]:
        return tuple(version for version in era if version in self.enabled)


def _unsupported(requested: object, supported: tuple[str, ...]) -> JsonRpcError:
    return JsonRpcError(INVALID_PARAMS, f"Unsupported protocol version: {requested}", {"supported": list(supported)})
