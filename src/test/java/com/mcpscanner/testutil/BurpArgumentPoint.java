package com.mcpscanner.testutil;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.Range;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPointType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.mcp.McpObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;

public final class BurpArgumentPoint implements AuditInsertionPoint {

    private final HttpRequest request;
    private final List<String> keys;

    public BurpArgumentPoint(HttpRequest request, String... keysUnderParams) {
        this.request = request;
        this.keys = Arrays.asList(keysUnderParams);
    }

    @Override
    public String name() {
        return keys.get(keys.size() - 1);
    }

    @Override
    public String baseValue() {
        return parent(body()).path(name()).asText();
    }

    @Override
    public HttpRequest buildHttpRequestWithPayload(ByteArray payload) {
        JsonNode body = body();
        ((ObjectNode) parent(body)).put(name(), payload.toString());
        return request.withBody(body.toString());
    }

    @Override
    public List<Range> issueHighlights(ByteArray payload) {
        return List.of();
    }

    @Override
    public AuditInsertionPointType type() {
        return AuditInsertionPointType.PARAM_JSON;
    }

    private JsonNode parent(JsonNode body) {
        JsonNode node = body.path("params");
        for (String key : keys.subList(0, keys.size() - 1)) {
            node = node.path(key);
        }
        return node;
    }

    private JsonNode body() {
        try {
            return McpObjectMapper.INSTANCE.readTree(request.bodyToString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
