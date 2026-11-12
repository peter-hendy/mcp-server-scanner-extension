package com.mcpscanner;

import com.mcpscanner.client.ConnectedEndpoint;
import com.mcpscanner.client.TransportType;

final class BoundEndpoint implements ConnectedEndpoint {

    private volatile ConnectedEndpoint bound;

    void bindTo(ConnectedEndpoint endpoint) {
        bound = endpoint;
    }

    @Override
    public TransportType transportType() {
        ConnectedEndpoint endpoint = bound;
        return endpoint == null ? null : endpoint.transportType();
    }

    @Override
    public String resolvedEndpoint() {
        ConnectedEndpoint endpoint = bound;
        return endpoint == null ? null : endpoint.resolvedEndpoint();
    }
}
