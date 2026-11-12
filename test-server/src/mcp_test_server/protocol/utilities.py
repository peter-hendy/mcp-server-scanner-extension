from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from mcp_test_server.protocol.catalog import Catalog
from mcp_test_server.protocol.context import CallContext
from mcp_test_server.protocol.families import Family
from mcp_test_server.protocol.jsonrpc import INVALID_PARAMS, JsonObject, JsonRpcError, required_string

LOG_LEVELS = ("debug", "info", "notice", "warning", "error", "critical", "alert", "emergency")


@dataclass(frozen=True)
class CompletionHandler:
    catalog: Catalog
    method: str = "completion/complete"
    family: Family = Family.COMPLETION

    async def handle(self, params: JsonObject, context: CallContext) -> JsonObject:
        reference = params.get("ref")
        argument = params.get("argument")
        if not isinstance(reference, dict) or not isinstance(argument, dict):
            raise JsonRpcError(INVALID_PARAMS, "completion/complete needs ref and argument")
        options = self._options(reference, str(argument.get("name", "")))
        prefix = str(argument.get("value", ""))
        values = [option for option in options if option.startswith(prefix)]
        return {"completion": {"values": values, "total": len(values), "hasMore": False}}

    def _options(self, reference: JsonObject, argument_name: str) -> Sequence[str]:
        if reference.get("type") == "ref/prompt":
            prompt = self.catalog.prompts.get(str(reference.get("name")))
            return prompt.completions.get(argument_name, ()) if prompt else ()
        if reference.get("type") == "ref/resource":
            uri = str(reference.get("uri"))
            for template in self.catalog.templates.values():
                if template.uri_template == uri:
                    return template.completions.get(argument_name, ())
            return ()
        raise JsonRpcError(INVALID_PARAMS, "Unknown completion reference type")


@dataclass(frozen=True)
class SetLevelHandler:
    method: str = "logging/setLevel"
    family: Family = Family.LOGGING

    async def handle(self, params: JsonObject, context: CallContext) -> JsonObject:
        if params.get("level") not in LOG_LEVELS:
            raise JsonRpcError(INVALID_PARAMS, "Unknown log level")
        return {}


@dataclass(frozen=True)
class SubscriptionHandler:
    method: str
    family: Family = Family.SUBSCRIBE

    async def handle(self, params: JsonObject, context: CallContext) -> JsonObject:
        required_string(params, "uri")
        return {}
