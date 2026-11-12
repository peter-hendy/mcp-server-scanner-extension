from __future__ import annotations

import pytest

from tests.conftest import ServerFactory
from tests.support import McpSession, tool_text

SERVICE_INDEX = "billing-service-index"


def _session(make_server: ServerFactory, vuln: str, mode: str) -> McpSession:
    return make_server(vulns={vuln: mode}).session()


@pytest.mark.parametrize("mode", ["vulnerable", "safe"])
@pytest.mark.parametrize(
    ("vuln", "tool", "argument", "normal"),
    [
        ("downstream_raw_segment", "get_invoice", "invoice_id", "INV-1001"),
        ("downstream_encoded_dot_segment", "get_order", "order_id", "ORD-2001"),
    ],
)
def test_normal_call_succeeds_in_both_modes(
    make_server: ServerFactory, vuln: str, tool: str, argument: str, normal: str, mode: str
) -> None:
    result = _session(make_server, vuln, mode).call_tool(tool, {argument: normal})
    assert result["isError"] is False
    assert normal in tool_text(result)


@pytest.mark.parametrize("payload", ["../", ".././", "../?mcp_route=1"])
def test_raw_segment_reaches_service_index_only_when_vulnerable(make_server: ServerFactory, payload: str) -> None:
    vulnerable = _session(make_server, "downstream_raw_segment", "vulnerable").call_tool(
        "get_invoice", {"invoice_id": payload})
    assert vulnerable["isError"] is False
    assert SERVICE_INDEX in tool_text(vulnerable)
    safe = _session(make_server, "downstream_raw_segment", "safe").call_tool("get_invoice", {"invoice_id": payload})
    assert SERVICE_INDEX not in tool_text(safe)


@pytest.mark.parametrize("payload", [".../", "..././", "...", "...."])
def test_raw_segment_controls_are_not_found_in_both_modes(make_server: ServerFactory, payload: str) -> None:
    for mode in ("vulnerable", "safe"):
        result = _session(make_server, "downstream_raw_segment", mode).call_tool("get_invoice", {"invoice_id": payload})
        assert SERVICE_INDEX not in tool_text(result)


def test_encoded_dot_segment_reaches_service_index_only_with_bare_dots(make_server: ServerFactory) -> None:
    session = _session(make_server, "downstream_encoded_dot_segment", "vulnerable")
    escaped = session.call_tool("get_order", {"order_id": ".."})
    assert escaped["isError"] is False
    assert SERVICE_INDEX in tool_text(escaped)
    for payload in ["../", ".././", "...", "...."]:
        assert SERVICE_INDEX not in tool_text(session.call_tool("get_order", {"order_id": payload}))


def test_encoded_dot_segment_rejects_dot_segments_when_safe(make_server: ServerFactory) -> None:
    result = _session(make_server, "downstream_encoded_dot_segment", "safe").call_tool("get_order", {"order_id": ".."})
    assert result["isError"] is True
    assert SERVICE_INDEX not in tool_text(result)
