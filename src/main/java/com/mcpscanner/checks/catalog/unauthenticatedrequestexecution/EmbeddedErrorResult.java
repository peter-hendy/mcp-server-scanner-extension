package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.checks.shared.auth.oracle.ReplyResults;
import com.mcpscanner.mcp.McpObjectMapper;

import java.util.List;
import java.util.stream.StreamSupport;

final class EmbeddedErrorResult {

    private static final int MAX_DEPTH = 4;
    private static final List<String> ERROR_KEYS = List.of("error", "errors");
    private static final List<String> WRAPPER_KEYS = List.of("data", "result");

    private final ReplyResults replies = new ReplyResults();

    boolean carriedBy(HttpRequestResponse exchange) {
        return replies.successfulResult(exchange).map(EmbeddedErrorResult::carries).orElse(false);
    }

    private static boolean carries(JsonNode result) {
        return StreamSupport.stream(result.path("content").spliterator(), false)
                .anyMatch(entry -> describesError(entry.path("text"), 0))
                || describesError(result.path("structuredContent"), 0);
    }

    private static boolean describesError(JsonNode node, int depth) {
        if (node == null || depth > MAX_DEPTH) {
            return false;
        }
        if (node.isTextual()) {
            return describesError(parsedObject(node.asText()), depth + 1);
        }
        return node.isObject() && (ERROR_KEYS.stream().anyMatch(key -> present(node.get(key)))
                || WRAPPER_KEYS.stream().anyMatch(key -> describesError(node.get(key), depth + 1)));
    }

    private static boolean present(JsonNode value) {
        return value != null && !value.isNull();
    }

    private static JsonNode parsedObject(String text) {
        String trimmed = text.strip();
        if (!trimmed.startsWith("{")) {
            return null;
        }
        try {
            return McpObjectMapper.INSTANCE.readTree(trimmed);
        } catch (JsonProcessingException unparseable) {
            return null;
        }
    }
}
