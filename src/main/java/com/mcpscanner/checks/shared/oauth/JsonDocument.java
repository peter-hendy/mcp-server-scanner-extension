package com.mcpscanner.checks.shared.oauth;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JsonDocument {

    private final JsonNode root;

    private JsonDocument(JsonNode root) {
        this.root = root;
    }

    public static Optional<JsonDocument> fromSuccessfulResponse(HttpRequestResponse exchange) {
        return successfulBody(exchange).flatMap(JsonDocument::parse);
    }

    private static Optional<String> successfulBody(HttpRequestResponse exchange) {
        int status = exchange.response().statusCode();
        return status >= 200 && status < 300 ? Optional.of(exchange.response().bodyToString()) : Optional.empty();
    }

    public static Optional<JsonDocument> parse(String body) {
        try {
            JsonNode root = McpObjectMapper.INSTANCE.readTree(body);
            return root != null && root.isObject() ? Optional.of(new JsonDocument(root)) : Optional.empty();
        } catch (Exception malformed) {
            return Optional.empty();
        }
    }

    public Optional<String> text(String field) {
        JsonNode value = root.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank()
                ? Optional.of(value.asText()) : Optional.empty();
    }

    public List<String> texts(String field) {
        JsonNode value = root.get(field);
        List<String> texts = new ArrayList<>();
        if (value != null && value.isArray()) {
            value.forEach(element -> {
                if (element.isTextual()) {
                    texts.add(element.asText());
                }
            });
        }
        return List.copyOf(texts);
    }

    public List<JsonNode> objects(String field) {
        JsonNode value = root.get(field);
        List<JsonNode> objects = new ArrayList<>();
        if (value != null && value.isArray()) {
            value.forEach(element -> {
                if (element.isObject()) {
                    objects.add(element);
                }
            });
        }
        return List.copyOf(objects);
    }

    public Optional<URI> webUri(String field) {
        return text(field).flatMap(HttpUris::parse);
    }
}
