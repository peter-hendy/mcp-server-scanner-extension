package com.mcpscanner.client;

import java.util.Objects;

public record PrimaryConnection(String endpoint, TransportType transport, ConnectResult catalog) {

    public PrimaryConnection {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(transport, "transport");
        Objects.requireNonNull(catalog, "catalog");
    }
}
