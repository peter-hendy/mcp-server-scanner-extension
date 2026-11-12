from __future__ import annotations

import argparse
import ssl
from collections.abc import Sequence
from pathlib import Path
from typing import Any

import uvicorn

from mcp_test_server.app import build_app
from mcp_test_server.modes import Mode
from mcp_test_server.oauth.server_context import ConsentMode
from mcp_test_server.protocol.families import Family
from mcp_test_server.protocol.versions import KNOWN_PROTOCOL_VERSIONS
from mcp_test_server.settings import load_settings, parse_vuln_assignments
from mcp_test_server.transport.sessions import SessionPolicy
from mcp_test_server.transport.streamable_http import ResponseMode
from mcp_test_server.vulns import registry


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="mcp-test-server", description="Intentionally vulnerable MCP server.")
    parser.add_argument("--config", type=Path, help="JSON settings file (lowest precedence)")
    parser.add_argument("--host")
    parser.add_argument("--port", type=int)
    parser.add_argument("--public-url", help="Externally visible base URL (issuer and audience)")
    parser.add_argument("--default-mode", choices=[m.value for m in Mode])
    parser.add_argument("--vuln", action="append", default=[], metavar="NAME=MODE",
                        help="Set one vulnerability to vulnerable or safe (repeatable)")
    parser.add_argument("--disable-family", action="append", default=[], choices=[f.value for f in Family],
                        help="Switch off one request family or transport (repeatable)")
    parser.add_argument("--page-size", type=int)
    parser.add_argument("--sessions", choices=[p.value for p in SessionPolicy])
    parser.add_argument("--response-mode", choices=[m.value for m in ResponseMode])
    parser.add_argument("--consent", choices=[m.value for m in ConsentMode])
    parser.add_argument("--files-dir", type=Path)
    parser.add_argument("--request-log", type=Path, help="Append every received request to this JSONL file")
    parser.add_argument("--command-timeout", type=float)
    parser.add_argument("--fetch-timeout", type=float)
    parser.add_argument("--allowed-host", action="append", default=[], dest="allowed_hosts",
                        help="Host admitted by safe Origin/Host admission (repeatable, replaces defaults)")
    parser.add_argument("--protocol-version", action="append", default=[], dest="protocol_versions",
                        choices=KNOWN_PROTOCOL_VERSIONS,
                        help="Enable one MCP protocol version (repeatable, replaces the default of all known)")
    parser.add_argument("--list-vulns", action="store_true", help="Print vulnerability names and exit")
    parser.add_argument("--tls-cert", metavar="PATH", help="PEM server certificate; with --tls-key, serve HTTPS")
    parser.add_argument("--tls-key", metavar="PATH", help="PEM server private key; with --tls-cert, serve HTTPS")
    parser.add_argument("--tls-client-ca", metavar="PATH",
                        help="PEM CA certificate; require a client certificate signed by it (mTLS)")
    return parser


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = build_parser()
    arguments = parser.parse_args(argv)
    tls_error = tls_combination_error(arguments)
    if tls_error:
        parser.error(tls_error)
    return arguments


def tls_combination_error(arguments: argparse.Namespace) -> str | None:
    if arguments.tls_client_ca and not (arguments.tls_cert or arguments.tls_key):
        return "--tls-client-ca requires --tls-cert and --tls-key"
    if arguments.tls_key and not arguments.tls_cert:
        return "--tls-key requires --tls-cert"
    if arguments.tls_cert and not arguments.tls_key:
        return "--tls-cert requires --tls-key"
    return None


def uvicorn_tls_kwargs(arguments: argparse.Namespace) -> dict[str, Any]:
    if not arguments.tls_cert:
        return {}
    kwargs: dict[str, Any] = {"ssl_certfile": arguments.tls_cert, "ssl_keyfile": arguments.tls_key}
    if arguments.tls_client_ca:
        kwargs.update(ssl_ca_certs=arguments.tls_client_ca, ssl_cert_reqs=ssl.CERT_REQUIRED)
    return kwargs


def cli_layer(arguments: argparse.Namespace) -> dict[str, Any]:
    layer = {
        key: value
        for key, value in vars(arguments).items()
        if key not in {"config", "vuln", "disable_family", "list_vulns", "allowed_hosts", "protocol_versions",
                       "tls_cert", "tls_key", "tls_client_ca"}
        and value is not None
    }
    if arguments.vuln:
        layer["vulns"] = parse_vuln_assignments(arguments.vuln)
    if arguments.disable_family:
        layer["disabled_families"] = arguments.disable_family
    if arguments.allowed_hosts:
        layer["allowed_hosts"] = arguments.allowed_hosts
    if arguments.protocol_versions:
        layer["protocol_versions"] = arguments.protocol_versions
    return layer


def main(argv: Sequence[str] | None = None) -> None:
    arguments = parse_args(argv)
    if arguments.list_vulns:
        for vulnerability in registry.vulnerability_types():
            print(f"{vulnerability.name}\t{vulnerability.summary}")
        return
    settings = load_settings(cli_layer(arguments), arguments.config)
    uvicorn.run(build_app(settings), host=settings.host, port=settings.port, log_level="info",
                **uvicorn_tls_kwargs(arguments))
