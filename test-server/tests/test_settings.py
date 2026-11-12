from __future__ import annotations

import json
from pathlib import Path

import pytest

from mcp_test_server.app import ServerAssembly
from mcp_test_server.cli import build_parser, cli_layer, main
from mcp_test_server.modes import Mode
from mcp_test_server.protocol.families import Family
from mcp_test_server.settings import ServerSettings, load_settings, settings_from_environment


def test_cli_layer_parses_vulns_families_and_hosts() -> None:
    arguments = build_parser().parse_args(
        ["--vuln", "jwt_alg_none=safe", "--vuln", "traversal_plain=vulnerable", "--disable-family", "ping",
         "--allowed-host", "mcp-test-server", "--default-mode", "safe", "--port", "9000"]
    )
    layer = cli_layer(arguments)
    assert layer["vulns"] == {"jwt_alg_none": "safe", "traversal_plain": "vulnerable"}
    assert layer["disabled_families"] == ["ping"]
    assert layer["allowed_hosts"] == ["mcp-test-server"]
    settings = ServerSettings.from_mapping(layer)
    assert settings.default_mode is Mode.SAFE
    assert settings.disabled_families == (Family.PING,)
    assert settings.port == 9000


def test_environment_layer() -> None:
    raw = settings_from_environment({"MCP_TEST_VULNS": "jwt_alg_none=safe, origin_admission=vulnerable",
                                     "MCP_TEST_DISABLED_FAMILIES": "sse,ping", "MCP_TEST_PAGE_SIZE": "5"})
    assert raw == {"vulns": {"jwt_alg_none": "safe", "origin_admission": "vulnerable"},
                   "disabled_families": ["sse", "ping"], "page_size": "5"}


def test_precedence_file_then_environment_then_cli(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    config = tmp_path / "config.json"
    config.write_text(json.dumps({"page_size": 2, "vulns": {"jwt_alg_none": "safe", "traversal_plain": "safe"},
                                  "disabled_families": ["ping"]}))
    monkeypatch.setenv("MCP_TEST_PAGE_SIZE", "4")
    monkeypatch.setenv("MCP_TEST_VULNS", "traversal_plain=vulnerable")
    settings = load_settings({"vulns": {"jwt_alg_none": "vulnerable"}, "disabled_families": ["sse"]}, config)
    assert settings.page_size == 4
    assert settings.vulns == {"jwt_alg_none": Mode.VULNERABLE, "traversal_plain": Mode.VULNERABLE}
    assert settings.disabled_families == (Family.PING, Family.SSE)


def test_config_path_from_environment(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    config = tmp_path / "config.json"
    config.write_text(json.dumps({"consent": "auto"}))
    monkeypatch.setenv("MCP_TEST_CONFIG", str(config))
    assert load_settings({}, None).consent.value == "auto"


def test_invalid_values_are_rejected(tmp_path: Path) -> None:
    with pytest.raises(ValueError):
        ServerSettings.from_mapping({"default_mode": "maybe"})
    with pytest.raises(ValueError):
        ServerSettings.from_mapping({"disabled_families": ["nonsense"]})
    with pytest.raises(ValueError):
        ServerSettings.from_mapping({"unknown_key": 1})
    with pytest.raises(ValueError, match="Unknown vulnerabilities"):
        ServerAssembly(ServerSettings.from_mapping({"vulns": {"made_up": "safe"}, "files_dir": str(tmp_path)}))


def test_list_vulns(capsys: pytest.CaptureFixture[str]) -> None:
    main(["--list-vulns"])
    names = [line.split("\t")[0] for line in capsys.readouterr().out.splitlines()]
    assert "unauthenticated_execution" in names
    assert names == sorted(names)


def test_inspection_rejects_bad_mode_and_unknown_name(make_server) -> None:  # type: ignore[no-untyped-def]
    client = make_server().client
    assert client.put("/_test/vulns/jwt_alg_none", json={"mode": "sometimes"}).status_code == 400
    assert client.put("/_test/vulns/not_real", json={"mode": "safe"}).status_code == 404
    listed = client.put("/_test/vulns/jwt_alg_none", json={"mode": "safe"}).json()
    assert {"name": "jwt_alg_none", "mode": "safe"}.items() <= next(
        entry for entry in listed if entry["name"] == "jwt_alg_none"
    ).items()
