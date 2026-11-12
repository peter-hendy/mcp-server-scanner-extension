package com.mcpscanner.core.oauth;

import java.net.URI;

public record RegisteredClient(URI issuer, String clientId, String clientSecret) {
    @Override
    public String toString() {
        return "RegisteredClient[issuer=" + issuer + ", clientId=" + clientId + "]";
    }
}
