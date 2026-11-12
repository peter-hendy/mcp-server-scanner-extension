from __future__ import annotations

import sys

from mcp_test_server.vulns.injection.arithmetic import evaluate_arithmetic
from mcp_test_server.vulns.injection.single_argument_tool import SingleArgumentTool

_EVAL_SCRIPT = "import sys; print(eval(sys.argv[1]), end='')"


class PythonEvalInjection(SingleArgumentTool):
    name = "python_eval_injection"
    summary = "calculate passes the expression argument to Python eval() in a worker process"
    tool_name = "calculate"
    tool_description = "Evaluate an arithmetic expression"
    argument = "expression"

    async def run_vulnerable(self, value: str) -> str:
        return await self.runner.exec([sys.executable, "-c", _EVAL_SCRIPT, value])

    async def run_safe(self, value: str) -> str:
        return str(evaluate_arithmetic(value))
