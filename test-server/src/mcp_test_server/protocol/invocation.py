from __future__ import annotations

from dataclasses import dataclass

from mcp_test_server.protocol.catalog import Catalog
from mcp_test_server.protocol.context import CallContext
from mcp_test_server.protocol.families import Family, FamilySwitches
from mcp_test_server.protocol.jsonrpc import (
    INVALID_PARAMS,
    METHOD_NOT_FOUND,
    RESOURCE_NOT_FOUND,
    JsonObject,
    JsonRpcError,
    required_string,
)


@dataclass(frozen=True)
class ToolsCallHandler:
    catalog: Catalog
    method: str = "tools/call"
    family: Family = Family.TOOLS_CALL

    async def handle(self, params: JsonObject, context: CallContext) -> JsonObject:
        name = required_string(params, "name")
        tool = self.catalog.tools.get(name)
        if tool is None:
            raise JsonRpcError(INVALID_PARAMS, f"Unknown tool: {name}")
        arguments = params.get("arguments") or {}
        if not isinstance(arguments, dict):
            raise JsonRpcError(INVALID_PARAMS, "Tool arguments must be an object")
        return await tool.handler(arguments, context)


@dataclass(frozen=True)
class ResourcesReadHandler:
    catalog: Catalog
    families: FamilySwitches
    method: str = "resources/read"
    family: Family = Family.RESOURCES_READ

    async def handle(self, params: JsonObject, context: CallContext) -> JsonObject:
        uri = required_string(params, "uri")
        resource = self.catalog.resources.get(uri)
        if resource is not None:
            return await resource.handler(uri, context)
        return await self._read_templated(uri, context)

    async def _read_templated(self, uri: str, context: CallContext) -> JsonObject:
        matched = self.catalog.matching_template(uri)
        if matched is None:
            raise JsonRpcError(RESOURCE_NOT_FOUND, "Resource not found", {"uri": uri})
        if not self.families.enabled(Family.RESOURCE_TEMPLATES_READ):
            raise JsonRpcError(METHOD_NOT_FOUND, "Resource template reads are disabled")
        template, variables = matched
        return await template.handler(uri, variables, context)


@dataclass(frozen=True)
class PromptsGetHandler:
    catalog: Catalog
    method: str = "prompts/get"
    family: Family = Family.PROMPTS_GET

    async def handle(self, params: JsonObject, context: CallContext) -> JsonObject:
        name = required_string(params, "name")
        prompt = self.catalog.prompts.get(name)
        if prompt is None:
            raise JsonRpcError(INVALID_PARAMS, f"Unknown prompt: {name}")
        arguments = params.get("arguments") or {}
        if not isinstance(arguments, dict):
            raise JsonRpcError(INVALID_PARAMS, "Prompt arguments must be an object")
        missing = [arg.name for arg in prompt.arguments if arg.required and arg.name not in arguments]
        if missing:
            raise JsonRpcError(INVALID_PARAMS, f"Missing prompt arguments: {', '.join(missing)}")
        return await prompt.handler({key: str(value) for key, value in arguments.items()}, context)
