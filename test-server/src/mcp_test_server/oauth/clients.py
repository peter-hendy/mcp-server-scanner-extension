from __future__ import annotations

import secrets
import threading
from dataclasses import dataclass, field
from typing import Any

PUBLIC_AUTH_METHOD = "none"


@dataclass(frozen=True)
class RegisteredClient:
    client_id: str
    client_name: str
    redirect_uris: tuple[str, ...]
    auth_method: str = PUBLIC_AUTH_METHOD
    secret: str | None = None
    metadata: dict[str, Any] = field(default_factory=dict)

    @property
    def confidential(self) -> bool:
        return self.secret is not None

    def describe(self) -> dict[str, Any]:
        described = {
            **self.metadata,
            "client_id": self.client_id,
            "client_name": self.client_name,
            "redirect_uris": list(self.redirect_uris),
            "token_endpoint_auth_method": self.auth_method,
            "grant_types": ["authorization_code", "refresh_token"],
            "response_types": ["code"],
        }
        if self.secret is not None:
            described["client_secret"] = self.secret
        return described


PRECONFIGURED_CLIENTS = (
    RegisteredClient(
        client_id="public-client",
        client_name="Preconfigured public client",
        redirect_uris=("https://client.example/callback", "http://127.0.0.1/callback"),
    ),
    RegisteredClient(
        client_id="confidential-client",
        client_name="Preconfigured confidential client",
        redirect_uris=("https://client.example/callback", "http://127.0.0.1/callback"),
        auth_method="client_secret_basic",
        secret="confidential-secret",
    ),
)


class ClientRegistry:
    def __init__(self) -> None:
        self._clients = {client.client_id: client for client in PRECONFIGURED_CLIENTS}
        self._lock = threading.Lock()

    def get(self, client_id: str) -> RegisteredClient | None:
        with self._lock:
            return self._clients.get(client_id)

    def register(self, metadata: dict[str, Any], redirect_uris: list[str]) -> RegisteredClient:
        auth_method = str(metadata.get("token_endpoint_auth_method") or PUBLIC_AUTH_METHOD)
        client = RegisteredClient(
            client_id=f"dcr-{secrets.token_hex(8)}",
            client_name=str(metadata.get("client_name") or "Unnamed client"),
            redirect_uris=tuple(redirect_uris),
            auth_method=auth_method,
            secret=None if auth_method == PUBLIC_AUTH_METHOD else secrets.token_urlsafe(24),
            metadata={k: v for k, v in metadata.items() if k not in {"client_id", "client_secret"}},
        )
        with self._lock:
            self._clients[client.client_id] = client
        return client
