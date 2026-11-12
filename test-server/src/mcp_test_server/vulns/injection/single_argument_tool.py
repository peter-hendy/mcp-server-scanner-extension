from __future__ import annotations

from abc import abstractmethod
from typing import ClassVar

from mcp_test_server.protocol.catalog import Arguments, Tool, text_content
from mcp_test_server.protocol.context import CallContext
from mcp_test_server.protocol.jsonrpc import JsonObject
from mcp_test_server.surface import ServerSurface
from mcp_test_server.vulns.base import Vulnerability
from mcp_test_server.vulns.injection.process_runner import ProcessRunner


class InputRejected(Exception):
    pass


class SingleArgumentTool(Vulnerability):
    tool_name: ClassVar[str]
    tool_description: ClassVar[str]
    argument: ClassVar[str]

    def contribute(self, surface: ServerSurface) -> None:
        self.runner = ProcessRunner(surface.environment.command_timeout_seconds)
        schema = {
            "type": "object",
            "properties": {self.argument: {"type": "string"}},
            "required": [self.argument],
        }
        surface.catalog.add_tool(Tool(self.tool_name, self.tool_description, schema, self._call))

    async def _call(self, arguments: Arguments, context: CallContext) -> JsonObject:
        value = str(arguments.get(self.argument, ""))
        try:
            output = await (self.run_vulnerable(value) if self.vulnerable else self.run_safe(value))
        except InputRejected as rejection:
            return text_content(f"Error: {rejection}", is_error=True)
        return text_content(output)

    @abstractmethod
    async def run_vulnerable(self, value: str) -> str: ...

    @abstractmethod
    async def run_safe(self, value: str) -> str: ...
