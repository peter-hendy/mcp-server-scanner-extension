from __future__ import annotations

from mcp_test_server.protocol.catalog import (
    Arguments,
    Prompt,
    PromptArgument,
    Resource,
    ResourceHandler,
    ResourceTemplate,
    Tool,
    prompt_messages,
    resource_contents,
    text_content,
)
from mcp_test_server.protocol.context import CallContext
from mcp_test_server.protocol.jsonrpc import JsonObject
from mcp_test_server.surface import ServerSurface

USERS = {"1": "Alice Example", "2": "Bob Example"}
LANGUAGES = ("python", "java", "javascript", "go")
TENANT_LOOKUP_SCHEMA = {
    "type": "object",
    "properties": {
        "tenant": {"type": "string", "x-mcp-header": "Tenant"},
        "priority": {"type": "integer", "x-mcp-header": "Priority"},
    },
    "required": ["tenant"],
}


class BenignCatalog:
    def contribute(self, surface: ServerSurface) -> None:
        catalog = surface.catalog
        catalog.add_tool(Tool("echo", "Echo a message", _object({"message": {"type": "string"}}), _echo))
        catalog.add_tool(Tool("add", "Add two integers",
                              _object({"a": {"type": "integer"}, "b": {"type": "integer"}}), _add))
        catalog.add_tool(Tool("tenant_lookup", "Look up a tenant; 2026-07-28 clients repeat its arguments in headers",
                              TENANT_LOOKUP_SCHEMA, _tenant_lookup))
        catalog.add_tool(Tool("delete_note", "Delete a note by id", _object({"note_id": {"type": "string"}}),
                              _delete_note, destructive=True))
        catalog.add_resource(Resource("docs://readme", "readme", "Project readme", _static("Benign readme")))
        catalog.add_resource(Resource("docs://changelog", "changelog", "Changelog", _static("v2.0.0: rewrite")))
        catalog.add_resource(Resource("status://health", "health", "Health status", _static('{"status":"ok"}'),
                                      mime_type="application/json"))
        catalog.add_template(ResourceTemplate("users://{user_id}/profile", "user_profile", "User profile by id",
                                              _user_profile, completions={"user_id": sorted(USERS)}))
        catalog.add_prompt(Prompt("summarize", "Summarize text", [PromptArgument("text", "Text to summarize")],
                                  _summarize))
        catalog.add_prompt(Prompt("code_review", "Review code",
                                  [PromptArgument("language", "Language"), PromptArgument("code", "Code")],
                                  _code_review, completions={"language": LANGUAGES}))


def _object(properties: JsonObject) -> JsonObject:
    return {"type": "object", "properties": properties, "required": sorted(properties)}


async def _echo(arguments: Arguments, context: CallContext) -> JsonObject:
    return text_content(str(arguments.get("message", "")))


async def _add(arguments: Arguments, context: CallContext) -> JsonObject:
    try:
        return text_content(str(int(arguments.get("a", 0)) + int(arguments.get("b", 0))))
    except (TypeError, ValueError):
        return text_content("Error: a and b must be integers", is_error=True)


async def _tenant_lookup(arguments: Arguments, context: CallContext) -> JsonObject:
    return text_content(f"Tenant {arguments.get('tenant', '')} at priority {arguments.get('priority', 0)}")


async def _delete_note(arguments: Arguments, context: CallContext) -> JsonObject:
    return text_content(f"Deleted note {arguments.get('note_id', '')}")


def _static(text: str) -> ResourceHandler:
    async def read(uri: str, context: CallContext) -> JsonObject:
        return resource_contents(uri, text)

    return read


async def _user_profile(uri: str, variables: dict[str, str], context: CallContext) -> JsonObject:
    return resource_contents(uri, USERS.get(variables["user_id"], "unknown user"))


async def _summarize(arguments: dict[str, str], context: CallContext) -> JsonObject:
    return prompt_messages("Summary request", f"Summarize:\n{arguments['text']}")


async def _code_review(arguments: dict[str, str], context: CallContext) -> JsonObject:
    return prompt_messages("Code review", f"Review this {arguments['language']} code:\n{arguments['code']}")
