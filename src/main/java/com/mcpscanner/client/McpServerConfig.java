package com.mcpscanner.client;

import com.mcpscanner.auth.AuthStrategy;

import java.net.URI;
import java.util.Objects;

public record McpServerConfig(String endpoint, TransportType transport, AuthStrategy auth) {
    public McpServerConfig {
        Objects.requireNonNull(auth, "auth");
        auth.validateEndpoint(URI.create(endpoint));
    }
}
