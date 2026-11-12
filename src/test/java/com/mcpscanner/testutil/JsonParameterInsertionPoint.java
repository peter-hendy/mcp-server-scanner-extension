package com.mcpscanner.testutil;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.Range;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPointType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.mcp.McpObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

public final class JsonParameterInsertionPoint implements AuditInsertionPoint {

    private final HttpRequest request;
    private final String name;

    public JsonParameterInsertionPoint(HttpRequest request, String name) {
        this.request = request;
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String baseValue() {
        return body().path(name).asText();
    }

    @Override
    public HttpRequest buildHttpRequestWithPayload(ByteArray payload) {
        ObjectNode body = body();
        body.put(name, payload.toString());
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

    private ObjectNode body() {
        try {
            return (ObjectNode) McpObjectMapper.INSTANCE.readTree(request.bodyToString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
