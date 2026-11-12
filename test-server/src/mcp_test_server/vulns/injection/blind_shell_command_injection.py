from __future__ import annotations

import re

from mcp_test_server.vulns.injection.single_argument_tool import InputRejected, SingleArgumentTool

QUEUED = "archive queued"
_LABEL = re.compile(r"^[A-Za-z0-9_-]{1,64}$")


class BlindShellCommandInjection(SingleArgumentTool):
    name = "blind_shell_command_injection"
    summary = "archive_logs interpolates the label argument into a sh -c command line and discards its output"
    tool_name = "archive_logs"
    tool_description = "Queue an archive of the server logs under a label"
    argument = "label"

    async def run_vulnerable(self, value: str) -> str:
        await self.runner.shell(f"tar -cf /tmp/mcp-test-archive-{value}.tar /dev/null >/dev/null 2>&1")
        return QUEUED

    async def run_safe(self, value: str) -> str:
        if not _LABEL.match(value):
            raise InputRejected("invalid archive label")
        return QUEUED
