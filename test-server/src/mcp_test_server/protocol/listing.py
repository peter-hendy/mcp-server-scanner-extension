from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass

from mcp_test_server.protocol.catalog import Catalog
from mcp_test_server.protocol.context import CallContext
from mcp_test_server.protocol.families import Family
from mcp_test_server.protocol.jsonrpc import JsonObject
from mcp_test_server.protocol.pagination import Paginator


@dataclass(frozen=True)
class ListHandler:
    method: str
    family: Family
    result_key: str
    entries: Callable[[CallContext], list[JsonObject]]
    paginator: Paginator

    async def handle(self, params: JsonObject, context: CallContext) -> JsonObject:
        return self.paginator.page(self.result_key, self.entries(context), params.get("cursor"))


def list_handlers(catalog: Catalog, paginator: Paginator) -> list[ListHandler]:
    return [
        ListHandler(
            "tools/list",
            Family.TOOLS_LIST,
            "tools",
            lambda context: [tool.describe() for tool in catalog.tools.values() if tool.visible_to(context)],
            paginator,
        ),
        ListHandler(
            "resources/list",
            Family.RESOURCES_LIST,
            "resources",
            lambda context: [resource.describe() for resource in catalog.resources.values()
                             if resource.visible_to(context)],
            paginator,
        ),
        ListHandler(
            "resources/templates/list",
            Family.RESOURCE_TEMPLATES_LIST,
            "resourceTemplates",
            lambda context: [template.describe() for template in catalog.templates.values()],
            paginator,
        ),
        ListHandler(
            "prompts/list",
            Family.PROMPTS_LIST,
            "prompts",
            lambda context: [prompt.describe() for prompt in catalog.prompts.values()],
            paginator,
        ),
    ]
