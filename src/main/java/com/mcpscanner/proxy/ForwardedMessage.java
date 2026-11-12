package com.mcpscanner.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;

import java.io.IOException;
import java.util.Optional;

record ForwardedMessage(Optional<String> id, Optional<String> method) {

    static ForwardedMessage of(String body) {
        return new ForwardedMessage(idOf(body), methodOf(body));
    }

    boolean wasSentIn(RecordedExchange exchange) {
        String sent = exchange.requestBody();
        if (id.isPresent()) {
            return id.equals(idOf(sent));
        }
        return method.isPresent() && method.equals(methodOf(sent));
    }

    private static Optional<String> idOf(String body) {
        return TopLevelId.in(body).map(TopLevelId::text);
    }

    private static Optional<String> methodOf(String body) {
        if (body == null || body.isEmpty()) {
            return Optional.empty();
        }
        try {
            JsonNode method = McpObjectMapper.INSTANCE.readTree(body).path("method");
            return method.isTextual() ? Optional.of(method.asText()) : Optional.empty();
        } catch (IOException unparsable) {
            return Optional.empty();
        }
    }
}
