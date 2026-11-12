package com.mcpscanner.ui;

import com.mcpscanner.client.TransportType;

import java.net.URI;
import java.net.URISyntaxException;

public record ConnectionSummary(TransportType requestedTransport, TransportType resolvedTransport,
                                String protocolVersion, String endpoint, String authType,
                                String sessionId) {

    public ConnectionSummary(TransportType requestedTransport, TransportType resolvedTransport,
                             String protocolVersion, String endpoint, String authType) {
        this(requestedTransport, resolvedTransport, protocolVersion, endpoint, authType, null);
    }

    public String displayEndpoint() {
        try {
            URI uri = URI.create(endpoint);
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(),
                    uri.getPath(), null, null).toString();
        } catch (IllegalArgumentException | URISyntaxException ex) {
            return "[invalid endpoint]";
        }
    }
}
