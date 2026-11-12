from __future__ import annotations

import re

from mcp_test_server.vulns.injection.single_argument_tool import InputRejected, SingleArgumentTool

_HOSTNAME = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9.-]{0,252})$")


class ShellCommandInjection(SingleArgumentTool):
    name = "shell_command_injection"
    summary = "ping_host interpolates the host argument into a sh -c command line"
    tool_name = "ping_host"
    tool_description = "Ping a host once and return the output"
    argument = "host"

    async def run_vulnerable(self, value: str) -> str:
        return await self.runner.shell(f"ping -c 1 -W 1 {value}")

    async def run_safe(self, value: str) -> str:
        if not _HOSTNAME.match(value):
            raise InputRejected("invalid host name")
        return await self.runner.exec(["ping", "-c", "1", "-W", "1", value])
