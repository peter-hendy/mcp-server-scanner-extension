package com.mcpscanner.mcp;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

public final class McpRequestNames {

    public String nameOf(HttpRequest request) {
        return jsonRpcMessage(request)
                .filter(message -> message.path("method").isTextual())
                .map(McpRequestNames::methodAndSubject)
                .orElseGet(() -> request.method() + " " + request.path());
    }

    private static String methodAndSubject(JsonNode message) {
        String method = message.get("method").asText();
        return subjectOf(message.path("params")).map(subject -> method + " " + subject).orElse(method);
    }

    private static Optional<String> subjectOf(JsonNode params) {
        return Optional.of(params.path("name")).filter(JsonNode::isTextual)
                .or(() -> Optional.of(params.path("uri")).filter(JsonNode::isTextual))
                .map(JsonNode::asText);
    }

    private static Optional<JsonNode> jsonRpcMessage(HttpRequest request) {
        try {
            return Optional.ofNullable(McpObjectMapper.INSTANCE.readTree(request.bodyToString()));
        } catch (JsonProcessingException | RuntimeException unparseable) {
            return Optional.empty();
        }
    }
}
