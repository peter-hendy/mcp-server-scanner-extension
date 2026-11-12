from __future__ import annotations

import json
from pathlib import Path

from tests.conftest import ServerFactory
from tests.support import TestServer


def test_logs_method_param_paths_and_headers(server: TestServer) -> None:
    session = server.session()
    session.call_tool("fetch_document", {"url": "x", "options": {"tags": ["a"], "timeout_seconds": 1}},
                      **{"x-probe": "1"})
    entry = server.requests()[-1]
    assert entry["path"] == "/mcp"
    assert entry["status"] == 200
    assert ["x-probe", "1"] in entry["headers"]
    assert entry["messages"][0]["method"] == "tools/call"
    assert entry["messages"][0]["param_paths"] == [
        "params.name",
        "params.arguments.url",
        "params.arguments.options.tags[0]",
        "params.arguments.options.timeout_seconds",
    ]


def test_logs_rejected_requests_and_skips_inspection_paths(make_server: ServerFactory) -> None:
    server = make_server(vulns={"unauthenticated_execution": "safe"})
    server.client.get("/_test/settings")
    response = server.session(initialize=False).initialize()
    assert response.status_code == 401
    entries = server.requests()
    assert [entry["status"] for entry in entries] == [401]
    assert entries[0]["messages"][0]["method"] == "initialize"


def test_clear_and_jsonl_file(make_server: ServerFactory, tmp_path: Path) -> None:
    log_file = tmp_path / "requests.jsonl"
    server = make_server(request_log=str(log_file))
    server.session().result("ping")
    lines = [json.loads(line) for line in log_file.read_text().splitlines()]
    assert [line["messages"][0]["method"] for line in lines] == ["initialize", "ping"]
    assert server.client.delete("/_test/requests").status_code == 204
    assert server.requests() == []
