package com.mcpscanner.checks.catalog.filetraversal;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.Range;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPointType;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.mcpscanner.testutil.ImmutableHttpRequest;

import java.util.List;
import java.util.Map;

final class PathArgumentInsertionPoint implements AuditInsertionPoint {

    static final String URL = "http://127.0.0.1:8765/mcp";

    private final String baseValue;

    PathArgumentInsertionPoint(String baseValue) {
        this.baseValue = baseValue;
    }

    static HttpRequest toolCall(String path) {
        String encoded = JsonNodeFactory.instance.textNode(path).toString();
        return ImmutableHttpRequest.post(URL, Map.of("Content-Type", "application/json"),
                "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"read_file\","
                        + "\"arguments\":{\"path\":" + encoded + "}}}");
    }

    HttpRequestResponse base() {
        return HttpRequestResponse.httpRequestResponse(toolCall(baseValue), HttpResponse.httpResponse(
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{}}"));
    }

    @Override
    public String name() {
        return "MCP field: params.arguments.path";
    }

    @Override
    public String baseValue() {
        return baseValue;
    }

    @Override
    public AuditInsertionPointType type() {
        return AuditInsertionPointType.PARAM_JSON;
    }

    @Override
    public HttpRequest buildHttpRequestWithPayload(ByteArray payload) {
        return toolCall(payload.toString());
    }

    @Override
    public List<Range> issueHighlights(ByteArray payload) {
        return List.of();
    }
}
