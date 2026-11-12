from __future__ import annotations

import ast
import operator
from collections.abc import Callable
from typing import Any

from mcp_test_server.vulns.injection.single_argument_tool import InputRejected

_BINARY: dict[type[ast.operator], Callable[[Any, Any], Any]] = {
    ast.Add: operator.add,
    ast.Sub: operator.sub,
    ast.Mult: operator.mul,
    ast.Div: operator.truediv,
    ast.FloorDiv: operator.floordiv,
    ast.Mod: operator.mod,
}
_UNARY: dict[type[ast.unaryop], Callable[[Any], Any]] = {ast.USub: operator.neg, ast.UAdd: operator.pos}


def evaluate_arithmetic(expression: str) -> int | float:
    try:
        tree = ast.parse(expression, mode="eval")
        result: int | float = _evaluate(tree.body)
        return result
    except (SyntaxError, ZeroDivisionError, ValueError, TypeError) as error:
        raise InputRejected(f"invalid arithmetic expression: {type(error).__name__}") from error


def _evaluate(node: ast.AST) -> Any:
    if isinstance(node, ast.Constant) and isinstance(node.value, int | float):
        return node.value
    if isinstance(node, ast.BinOp) and type(node.op) in _BINARY:
        return _BINARY[type(node.op)](_evaluate(node.left), _evaluate(node.right))
    if isinstance(node, ast.UnaryOp) and type(node.op) in _UNARY:
        return _UNARY[type(node.op)](_evaluate(node.operand))
    raise ValueError("unsupported expression")
