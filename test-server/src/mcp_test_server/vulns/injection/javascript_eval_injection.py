from __future__ import annotations

import re

from mcp_test_server.vulns.injection.single_argument_tool import SingleArgumentTool

TEMPLATE_VALUES = {"name": "world", "product": "MCP"}
_NODE_SCRIPT = (
    "const name = 'world'; const product = 'MCP';"
    "process.stdout.write(String(eval('`' + process.argv[1] + '`')));"
)
_PLACEHOLDER = re.compile(r"\$\{(\w+)\}")


class JavascriptEvalInjection(SingleArgumentTool):
    name = "javascript_eval_injection"
    summary = "render_greeting evaluates the template argument as a JavaScript template literal in node"
    tool_name = "render_greeting"
    tool_description = "Render a greeting template such as 'Hello ${name}'"
    argument = "template"

    async def run_vulnerable(self, value: str) -> str:
        return await self.runner.exec(["node", "-e", _NODE_SCRIPT, value])

    async def run_safe(self, value: str) -> str:
        return _PLACEHOLDER.sub(lambda match: TEMPLATE_VALUES.get(match.group(1), match.group(0)), value)
