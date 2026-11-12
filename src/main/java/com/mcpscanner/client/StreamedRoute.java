package com.mcpscanner.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.HttpOrigin;
import com.mcpscanner.mcp.McpObjectMapper;

import java.io.IOException;
import java.util.Optional;

record StreamedRoute(HttpOrigin origin, String method) {

    static StreamedRoute of(ProtocolHttpRequest request) {
        return new StreamedRoute(HttpOrigin.of(request.uri()),
                jsonRpcMethod(request.body()).orElse(request.method()));
    }

    private static Optional<String> jsonRpcMethod(String body) {
        if (body == null) {
            return Optional.empty();
        }
        try {
            JsonNode method = McpObjectMapper.INSTANCE.readTree(body).path("method");
            return method.isTextual() ? Optional.of(method.asText()) : Optional.empty();
        } catch (IOException | RuntimeException unparseable) {
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return method + " to " + origin;
    }
}
