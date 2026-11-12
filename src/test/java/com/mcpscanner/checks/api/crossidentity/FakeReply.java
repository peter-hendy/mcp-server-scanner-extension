package com.mcpscanner.checks.api.crossidentity;

import java.util.Map;

public record FakeReply(int status, String body, Map<String, String> headers) {

    public static FakeReply result(String text) {
        return json("{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\""
                + text + "\"}]}}");
    }

    public static FakeReply toolError(String text) {
        return json("{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"isError\":true,\"content\":[{\"type\":\"text\","
                + "\"text\":\"" + text + "\"}]}}");
    }

    public static FakeReply rpcError(int code, String message) {
        return json("{\"jsonrpc\":\"2.0\",\"id\":7,\"error\":{\"code\":" + code + ",\"message\":\"" + message + "\"}}");
    }

    public static FakeReply resource(String text) {
        return json("{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"contents\":[{\"uri\":\"x\",\"text\":\"" + text + "\"}]}}");
    }

    public static FakeReply unauthorized() {
        return new FakeReply(401, "{\"error\":\"unauthorized\"}", Map.of());
    }

    public static FakeReply sessionNotFound() {
        return new FakeReply(404, "{\"error\":\"Session not found\"}", Map.of());
    }

    public static FakeReply initialized(String sessionId) {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":\"i\",\"result\":{\"protocolVersion\":\"2025-06-18\"}}";
        return sessionId == null ? new FakeReply(200, body, Map.of())
                : new FakeReply(200, body, Map.of("Mcp-Session-Id", sessionId));
    }

    public static FakeReply accepted() {
        return new FakeReply(202, "", Map.of());
    }

    public static FakeReply json(String body) {
        return new FakeReply(200, body, Map.of());
    }

    public String raw() {
        StringBuilder raw = new StringBuilder("HTTP/1.1 ").append(status).append(" X\r\n")
                .append("Content-Type: application/json\r\n");
        headers.forEach((name, value) -> raw.append(name).append(": ").append(value).append("\r\n"));
        return raw.append("\r\n").append(body).toString();
    }
}
