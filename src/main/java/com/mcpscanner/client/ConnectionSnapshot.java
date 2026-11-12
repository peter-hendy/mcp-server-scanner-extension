package com.mcpscanner.client;

public record ConnectionSnapshot(long generation, String endpoint, TransportType transportType,
                                 String sseUrl, String negotiatedProtocolVersion) {

    static ConnectionSnapshot disconnected(long generation) {
        return new ConnectionSnapshot(generation, null, null, null, null);
    }
}
