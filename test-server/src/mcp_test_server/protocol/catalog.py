from __future__ import annotations

import re
from collections.abc import Awaitable, Callable, Sequence
from dataclasses import dataclass, field
from typing import Any

from mcp_test_server.protocol.context import CallContext
from mcp_test_server.protocol.jsonrpc import JsonObject

Arguments = dict[str, Any]
ToolHandler = Callable[[Arguments, CallContext], Awaitable[JsonObject]]
ResourceHandler = Callable[[str, CallContext], Awaitable[JsonObject]]
TemplateHandler = Callable[[str, dict[str, str], CallContext], Awaitable[JsonObject]]
PromptHandler = Callable[[dict[str, str], CallContext], Awaitable[JsonObject]]
Visibility = Callable[[CallContext], bool]


def visible_to_everyone(context: CallContext) -> bool:
    return True


@dataclass(frozen=True)
class Tool:
    name: str
    description: str
    input_schema: JsonObject
    handler: ToolHandler
    destructive: bool = False
    visible_to: Visibility = visible_to_everyone

    def describe(self) -> JsonObject:
        return {
            "name": self.name,
            "description": self.description,
            "inputSchema": self.input_schema,
            "annotations": {"destructiveHint": self.destructive, "readOnlyHint": not self.destructive},
        }


@dataclass(frozen=True)
class Resource:
    uri: str
    name: str
    description: str
    handler: ResourceHandler
    mime_type: str = "text/plain"
    visible_to: Visibility = visible_to_everyone

    def describe(self) -> JsonObject:
        return {
            "uri": self.uri,
            "name": self.name,
            "description": self.description,
            "mimeType": self.mime_type,
        }


@dataclass(frozen=True)
class ResourceTemplate:
    uri_template: str
    name: str
    description: str
    handler: TemplateHandler
    completions: dict[str, Sequence[str]] = field(default_factory=dict)
    mime_type: str = "text/plain"

    def describe(self) -> JsonObject:
        return {
            "uriTemplate": self.uri_template,
            "name": self.name,
            "description": self.description,
            "mimeType": self.mime_type,
        }

    def match(self, uri: str) -> dict[str, str] | None:
        found = re.fullmatch(self._pattern(), uri, flags=re.DOTALL)
        return found.groupdict() if found else None

    def _pattern(self) -> str:
        pattern = ""
        for literal, variable in re.findall(r"([^{]*)(\{[^}]*\})?", self.uri_template):
            pattern += re.escape(literal)
            if variable:
                pattern += self._variable_pattern(variable[1:-1])
        return pattern

    @staticmethod
    def _variable_pattern(expression: str) -> str:
        name = expression.lstrip("+#")
        reserved = expression.startswith(("+", "#"))
        return f"(?P<{name}>.+)" if reserved else f"(?P<{name}>[^/]+)"


@dataclass(frozen=True)
class PromptArgument:
    name: str
    description: str
    required: bool = True

    def describe(self) -> JsonObject:
        return {"name": self.name, "description": self.description, "required": self.required}


@dataclass(frozen=True)
class Prompt:
    name: str
    description: str
    arguments: Sequence[PromptArgument]
    handler: PromptHandler
    completions: dict[str, Sequence[str]] = field(default_factory=dict)

    def describe(self) -> JsonObject:
        return {
            "name": self.name,
            "description": self.description,
            "arguments": [argument.describe() for argument in self.arguments],
        }


class Catalog:
    def __init__(self) -> None:
        self.tools: dict[str, Tool] = {}
        self.resources: dict[str, Resource] = {}
        self.templates: dict[str, ResourceTemplate] = {}
        self.prompts: dict[str, Prompt] = {}

    def add_tool(self, tool: Tool) -> None:
        self._add(self.tools, tool.name, tool)

    def add_resource(self, resource: Resource) -> None:
        self._add(self.resources, resource.uri, resource)

    def add_template(self, template: ResourceTemplate) -> None:
        self._add(self.templates, template.name, template)

    def add_prompt(self, prompt: Prompt) -> None:
        self._add(self.prompts, prompt.name, prompt)

    def matching_template(self, uri: str) -> tuple[ResourceTemplate, dict[str, str]] | None:
        for template in self.templates.values():
            variables = template.match(uri)
            if variables is not None:
                return template, variables
        return None

    @staticmethod
    def _add(entries: dict[str, Any], key: str, entry: Any) -> None:
        if key in entries:
            raise ValueError(f"Duplicate catalog entry: {key}")
        entries[key] = entry


def text_content(text: str, *, is_error: bool = False) -> JsonObject:
    return {"content": [{"type": "text", "text": text}], "isError": is_error}


def resource_contents(uri: str, text: str, mime_type: str = "text/plain") -> JsonObject:
    return {"contents": [{"uri": uri, "mimeType": mime_type, "text": text}]}


def prompt_messages(description: str, text: str) -> JsonObject:
    return {
        "description": description,
        "messages": [{"role": "user", "content": {"type": "text", "text": text}}],
    }
