package com.mcpscanner.checks.shared.jsonrpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.mcp.SseResponseParser;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public final class JsonRpcMessages {

    private static final String JSONRPC_VERSION = "2.0";

    private JsonRpcMessages() {}

    public static List<JsonNode> in(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        String trimmed = body.stripLeading();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return parsed(trimmed).map(JsonRpcMessages::flattened).orElse(List.of());
        }
        return SseResponseParser.eventData(body).stream()
                .map(JsonRpcMessages::parsed)
                .flatMap(Optional::stream)
                .flatMap(node -> flattened(node).stream())
                .toList();
    }

    public static Optional<JsonNode> parsed(String json) {
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(McpObjectMapper.INSTANCE.readTree(json));
        } catch (JsonProcessingException unparseable) {
            return Optional.empty();
        }
    }

    public static Optional<JsonNode> parsed(byte[] json) {
        if (json == null || json.length == 0) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(McpObjectMapper.INSTANCE.readTree(json));
        } catch (IOException unparseable) {
            return Optional.empty();
        }
    }

    public static boolean isJsonRpc2(JsonNode message) {
        return JSONRPC_VERSION.equals(message.path("jsonrpc").asText(null));
    }

    private static List<JsonNode> flattened(JsonNode node) {
        return node.isArray() ? JsonNodes.elements(node) : List.of(node);
    }
}
