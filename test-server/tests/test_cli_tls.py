from __future__ import annotations

import ssl

import pytest

from mcp_test_server import cli


def test_tls_flags_default_to_plain_http() -> None:
    options = cli.parse_args([])

    assert options.tls_cert is None
    assert options.tls_key is None
    assert options.tls_client_ca is None
    assert cli.uvicorn_tls_kwargs(options) == {}


def test_help_lists_tls_flags(capsys: pytest.CaptureFixture[str]) -> None:
    with pytest.raises(SystemExit):
        cli.main(["--help"])

    output = capsys.readouterr().out
    assert "--tls-cert" in output
    assert "--tls-key" in output
    assert "--tls-client-ca" in output


def test_cert_and_key_serve_https_without_requiring_client_certificate() -> None:
    options = cli.parse_args(["--tls-cert", "server.pem", "--tls-key", "server.key"])

    assert cli.uvicorn_tls_kwargs(options) == {
        "ssl_certfile": "server.pem",
        "ssl_keyfile": "server.key",
    }


def test_client_ca_requires_client_certificate_signed_by_it() -> None:
    options = cli.parse_args(
        ["--tls-cert", "server.pem", "--tls-key", "server.key", "--tls-client-ca", "ca.pem"]
    )

    assert cli.uvicorn_tls_kwargs(options) == {
        "ssl_certfile": "server.pem",
        "ssl_keyfile": "server.key",
        "ssl_ca_certs": "ca.pem",
        "ssl_cert_reqs": ssl.CERT_REQUIRED,
    }


@pytest.mark.parametrize(
    ("argv", "expected_error"),
    [
        (["--tls-key", "server.key"], "--tls-key requires --tls-cert"),
        (["--tls-cert", "server.pem"], "--tls-cert requires --tls-key"),
        (["--tls-client-ca", "ca.pem"], "--tls-client-ca requires --tls-cert and --tls-key"),
        (["--tls-client-ca", "ca.pem", "--tls-cert", "server.pem"], "--tls-cert requires --tls-key"),
    ],
)
def test_incomplete_tls_flag_combinations_are_rejected(
    argv: list[str], expected_error: str, capsys: pytest.CaptureFixture[str]
) -> None:
    with pytest.raises(SystemExit) as exc_info:
        cli.parse_args(argv)

    assert exc_info.value.code != 0
    assert expected_error in capsys.readouterr().err


def test_main_passes_tls_kwargs_to_uvicorn(monkeypatch: pytest.MonkeyPatch) -> None:
    captured: dict[str, object] = {}
    monkeypatch.setattr(cli.uvicorn, "run", lambda app, **kwargs: captured.update(kwargs))

    cli.main(["--tls-cert", "server.pem", "--tls-key", "server.key", "--tls-client-ca", "ca.pem"])

    assert captured["ssl_certfile"] == "server.pem"
    assert captured["ssl_keyfile"] == "server.key"
    assert captured["ssl_ca_certs"] == "ca.pem"
    assert captured["ssl_cert_reqs"] == ssl.CERT_REQUIRED
