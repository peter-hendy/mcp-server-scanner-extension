from __future__ import annotations

from tests.conftest import ServerFactory
from tests.support import TestServer


def _strict(make_server: ServerFactory, **vulns: str) -> TestServer:
    return make_server(default_mode="safe", vulns={"unauthenticated_execution": "vulnerable", **vulns})


def _issued_ids(server: TestServer, count: int) -> list[str]:
    return [server.session().headers["mcp-session-id"] for _ in range(count)]


def test_session_ids_are_sequential_only_when_vulnerable(make_server: ServerFactory) -> None:
    vulnerable = [int(issued.rsplit("-", 1)[1]) for issued in
                  _issued_ids(_strict(make_server, session_guessable_ids="vulnerable"), 3)]
    assert vulnerable == [vulnerable[0], vulnerable[0] + 1, vulnerable[0] + 2]
    safe = _issued_ids(_strict(make_server), 3)
    assert len(set(safe)) == 3
    assert all(len(issued) == 32 for issued in safe)


def test_deleted_session_keeps_working_only_when_vulnerable(make_server: ServerFactory) -> None:
    vulnerable = _strict(make_server, session_delete_ignored="vulnerable").session()
    assert vulnerable.client.delete("/mcp", headers=vulnerable.headers).status_code == 200
    assert vulnerable.result("ping") == {}
    safe = _strict(make_server).session()
    assert safe.client.delete("/mcp", headers=safe.headers).status_code == 200
    assert safe.post(safe.message("ping")).status_code == 404
