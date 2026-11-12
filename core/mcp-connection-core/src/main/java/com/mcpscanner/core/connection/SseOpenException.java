package com.mcpscanner.core.connection;

import com.mcpscanner.core.protocol.McpFlow;

public final class SseOpenException extends RuntimeException {
    private final McpFlow.HttpResponse response;
    public SseOpenException(McpFlow.HttpResponse response) {
        super("SSE stream opening failed");
        this.response = java.util.Objects.requireNonNull(response);
    }
    public McpFlow.HttpResponse response() { return response; }
}
