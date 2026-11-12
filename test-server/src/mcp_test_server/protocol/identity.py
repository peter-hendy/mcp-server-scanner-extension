from __future__ import annotations

from dataclasses import dataclass

from mcp_test_server.protocol.jsonrpc import JsonObject


@dataclass(frozen=True)
class ServerIdentity:
    name: str = "mcp-scanner-test-server"
    version: str = "2.0.0-vuln-fixture"
    instructions: str = "Intentionally vulnerable MCP server for scanner validation."

    def server_info(self) -> JsonObject:
        return {"name": self.name, "version": self.version}
