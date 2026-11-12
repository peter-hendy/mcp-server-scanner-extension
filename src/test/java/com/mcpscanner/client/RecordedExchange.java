package com.mcpscanner.client;

import java.util.Map;

record RecordedExchange(String method, int status, Map<String, String> headers, String body) {
    private static final String METHOD_NOT_FOUND =
            "{\"jsonrpc\":\"2.0\",\"id\":0,\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}";

    static RecordedExchange methodNotFound(String method) {
        return new RecordedExchange(method, 200, Map.of("Content-Type", "application/json"), METHOD_NOT_FOUND);
    }

    boolean framedAsEventStream() {
        return headers.getOrDefault("Content-Type", "").startsWith("text/event-stream");
    }
}
