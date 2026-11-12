package com.mcpscanner.client;

public interface ConnectedEndpoint {

    TransportType transportType();

    String resolvedEndpoint();
}
