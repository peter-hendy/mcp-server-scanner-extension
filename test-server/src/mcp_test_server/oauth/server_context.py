from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from enum import StrEnum
from typing import Any, Protocol

from mcp_test_server.oauth.clients import ClientRegistry
from mcp_test_server.oauth.consent import ConsentPage
from mcp_test_server.oauth.grants import GrantStore
from mcp_test_server.oauth.redirect_policy import RedirectPolicy
from mcp_test_server.oauth.refresh_policy import RefreshPolicy
from mcp_test_server.oauth.signer import JwtSigner

DEFAULT_SCOPE = "mcp:tools"
SUPPORTED_SCOPES = ("mcp:tools", "mcp:resources", "mcp:prompts", "mcp:admin")
TEST_SUBJECT = "test-user"


class ConsentMode(StrEnum):
    PROMPT = "prompt"
    AUTO = "auto"


class RegistrationObserver(Protocol):
    def on_register(self, metadata: dict[str, Any]) -> None: ...


@dataclass(frozen=True)
class AuthorizationServerContext:
    base_url: str
    signer: JwtSigner
    clients: ClientRegistry
    grants: GrantStore
    redirect_policy: RedirectPolicy
    refresh_policy: RefreshPolicy
    consent_page: ConsentPage
    consent_mode: ConsentMode
    registration_observers: Sequence[RegistrationObserver]
