from __future__ import annotations

import json
import os
from collections.abc import Mapping
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from mcp_test_server.modes import Mode
from mcp_test_server.oauth.server_context import ConsentMode
from mcp_test_server.protocol.families import Family
from mcp_test_server.protocol.versions import KNOWN_PROTOCOL_VERSIONS, parse_protocol_versions
from mcp_test_server.transport.sessions import SessionPolicy
from mcp_test_server.transport.streamable_http import ResponseMode

ENV_PREFIX = "MCP_TEST_"
LIST_KEYS = ("disabled_families", "allowed_hosts", "protocol_versions")
DEFAULT_ALLOWED_HOSTS = ("localhost", "127.0.0.1", "::1")


@dataclass(frozen=True)
class ServerSettings:
    host: str = "127.0.0.1"
    port: int = 8000
    public_url: str | None = None
    default_mode: Mode = Mode.VULNERABLE
    vulns: Mapping[str, Mode] = field(default_factory=dict)
    disabled_families: tuple[Family, ...] = ()
    page_size: int = 3
    sessions: SessionPolicy = SessionPolicy.REQUIRED
    response_mode: ResponseMode = ResponseMode.JSON
    consent: ConsentMode = ConsentMode.PROMPT
    files_dir: Path | None = None
    request_log: Path | None = None
    command_timeout: float = 30.0
    fetch_timeout: float = 3.0
    allowed_hosts: tuple[str, ...] = DEFAULT_ALLOWED_HOSTS
    protocol_versions: tuple[str, ...] = KNOWN_PROTOCOL_VERSIONS

    @property
    def base_url(self) -> str:
        return (self.public_url or f"http://{self.host}:{self.port}").rstrip("/")

    @classmethod
    def from_mapping(cls, raw: Mapping[str, Any]) -> ServerSettings:
        converters = _converters()
        unknown = set(raw) - set(converters)
        if unknown:
            raise ValueError(f"Unknown settings: {', '.join(sorted(unknown))}")
        return cls(**{key: converters[key](value) for key, value in raw.items() if value is not None})


def _converters() -> dict[str, Any]:
    return {
        "host": str,
        "port": int,
        "public_url": str,
        "default_mode": Mode,
        "vulns": lambda value: {str(name): Mode(mode) for name, mode in dict(value).items()},
        "disabled_families": lambda value: tuple(Family(item) for item in value),
        "page_size": int,
        "sessions": SessionPolicy,
        "response_mode": ResponseMode,
        "consent": ConsentMode,
        "files_dir": Path,
        "request_log": Path,
        "command_timeout": float,
        "fetch_timeout": float,
        "allowed_hosts": lambda value: tuple(str(item) for item in value),
        "protocol_versions": parse_protocol_versions,
    }


def parse_vuln_assignments(assignments: list[str]) -> dict[str, str]:
    parsed: dict[str, str] = {}
    for assignment in assignments:
        name, separator, mode = assignment.partition("=")
        if not separator:
            raise ValueError(f"Expected NAME=MODE, got {assignment!r}")
        parsed[name.strip()] = mode.strip()
    return parsed


def settings_from_environment(environ: Mapping[str, str]) -> dict[str, Any]:
    raw: dict[str, Any] = {}
    for key in _converters():
        value = environ.get(ENV_PREFIX + key.upper())
        if value is None:
            continue
        if key == "vulns":
            raw[key] = parse_vuln_assignments([item for item in value.split(",") if item.strip()])
        elif key in LIST_KEYS:
            raw[key] = [item.strip() for item in value.split(",") if item.strip()]
        else:
            raw[key] = value
    return raw


def settings_from_file(path: Path | None) -> dict[str, Any]:
    if path is None:
        return {}
    loaded = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(loaded, dict):
        raise ValueError("Config file must contain a JSON object")
    return loaded


def merge_layers(*layers: Mapping[str, Any]) -> dict[str, Any]:
    merged: dict[str, Any] = {}
    for layer in layers:
        for key, value in layer.items():
            if key == "vulns":
                merged["vulns"] = {**merged.get("vulns", {}), **value}
            elif key == "disabled_families":
                merged[key] = [*merged.get(key, []), *value]
            else:
                merged[key] = value
    return merged


def load_settings(cli_layer: Mapping[str, Any], config_path: Path | None) -> ServerSettings:
    config_path = config_path or _env_config_path(os.environ)
    layers = (settings_from_file(config_path), settings_from_environment(os.environ), cli_layer)
    return ServerSettings.from_mapping(merge_layers(*layers))


def _env_config_path(environ: Mapping[str, str]) -> Path | None:
    value = environ.get(ENV_PREFIX + "CONFIG")
    return Path(value) if value else None
