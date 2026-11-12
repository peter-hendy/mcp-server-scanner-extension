from __future__ import annotations

from typing import Any

from mcp_test_server.outbound.fetcher import OutboundFetcher, fetchable_url
from mcp_test_server.surface import ServerSurface
from mcp_test_server.vulns.base import Vulnerability

FETCHED_METADATA_FIELDS = ("client_uri", "logo_uri", "jwks_uri")


class FetchOauthClientMetadata(Vulnerability):
    name = "fetch_oauth_client_metadata"
    summary = "Dynamic client registration fetches client_uri, logo_uri and jwks_uri"

    def contribute(self, surface: ServerSurface) -> None:
        self._fetcher: OutboundFetcher = surface.environment.fetcher
        surface.registration_observers.append(self)

    def on_register(self, metadata: dict[str, Any]) -> None:
        if not self.vulnerable:
            return
        for field in FETCHED_METADATA_FIELDS:
            url = fetchable_url(metadata.get(field))
            if url is not None:
                self._fetcher.fetch_in_background(self.name, field, url)
