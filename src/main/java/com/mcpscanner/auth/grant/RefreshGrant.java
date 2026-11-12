package com.mcpscanner.auth.grant;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RefreshGrant(URI issuer, Optional<URI> resource, OAuthClient client, String refreshToken,
                           List<String> requestedScopes) {

    public RefreshGrant {
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(refreshToken, "refreshToken");
        requestedScopes = List.copyOf(requestedScopes);
    }

    @Override
    public String toString() {
        return "RefreshGrant[issuer=" + issuer + ", client=" + client + ", refreshToken=redacted]";
    }
}
