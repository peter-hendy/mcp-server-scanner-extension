from __future__ import annotations

from mcp_test_server.protocol.families import Family, FamilySwitches
from mcp_test_server.protocol.jsonrpc import JsonObject


class CapabilityAdvertiser:
    def __init__(self, families: FamilySwitches) -> None:
        self._families = families

    def capabilities(self) -> JsonObject:
        capabilities: JsonObject = {}
        if self._families.any_enabled(Family.TOOLS_CALL, Family.TOOLS_LIST):
            capabilities["tools"] = {"listChanged": False}
        if self._families.any_enabled(
            Family.RESOURCES_READ,
            Family.RESOURCES_LIST,
            Family.RESOURCE_TEMPLATES_LIST,
            Family.RESOURCE_TEMPLATES_READ,
        ):
            capabilities["resources"] = {
                "subscribe": self._families.enabled(Family.SUBSCRIBE),
                "listChanged": False,
            }
        if self._families.any_enabled(Family.PROMPTS_GET, Family.PROMPTS_LIST):
            capabilities["prompts"] = {"listChanged": False}
        if self._families.enabled(Family.COMPLETION):
            capabilities["completions"] = {}
        if self._families.enabled(Family.LOGGING):
            capabilities["logging"] = {}
        return capabilities
