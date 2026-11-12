package com.mcpscanner.ui;

import java.net.URI;
import java.util.Optional;

final class ConnectedEndpoint {

    private ConnectedEndpoint() {
    }

    static Optional<URI> of(String endpoint) {
        if (endpoint == null) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(endpoint.trim());
            return uri.getScheme() == null || uri.getHost() == null ? Optional.empty() : Optional.of(uri);
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }
}
