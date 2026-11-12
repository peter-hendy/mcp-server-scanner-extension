package com.mcpscanner.auth.grant;

import java.util.Objects;
import java.util.Optional;

public record OAuthClient(String clientId, Optional<String> secret, ClientType type) {

    public OAuthClient {
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(secret, "secret");
        Objects.requireNonNull(type, "type");
    }

    @Override
    public String toString() {
        return "OAuthClient[clientId=" + clientId + ", type=" + type + ", secret=redacted]";
    }
}
