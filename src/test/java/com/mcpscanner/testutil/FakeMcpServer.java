package com.mcpscanner.testutil;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.client.ProtocolHttpResponse;
import com.mcpscanner.client.ProtocolHttpRequest;
import com.mcpscanner.mcp.McpObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class FakeMcpServer {

    public static final String LEGACY_VERSION = "2025-03-26";
    public static final String SESSION_ID = "session-a";
    private static final String DISCOVER_RESULT = "{\"capabilities\":{},\"supportedVersions\":[\"2026-07-28\"]}";
    private static final String EMPTY_CATALOGS =
            "{\"tools\":[],\"resources\":[],\"resourceTemplates\":[],\"prompts\":[]}";

    private FakeMcpServer() {
    }

    public static ProtocolHttpResponse respond(ProtocolHttpRequest request) {
        String method = method(request);
        if (method == null || method.startsWith("notifications/")) {
            return exchange(request, 202, Map.of(), "");
        }
        if (method.equals("initialize")) {
            return exchange(request, 200, Map.of("Content-Type", List.of("application/json"),
                    "Mcp-Session-Id", List.of(SESSION_ID)), initializeReply(request));
        }
        String result = method.equals("server/discover") ? DISCOVER_RESULT : EMPTY_CATALOGS;
        return exchange(request, 200, Map.of("Content-Type", List.of("application/json")),
                "{\"jsonrpc\":\"2.0\",\"id\":" + id(request) + ",\"result\":" + result + "}");
    }

    public static String initializeReply(ProtocolHttpRequest request) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id(request) + ",\"result\":{\"protocolVersion\":\""
                + LEGACY_VERSION + "\",\"capabilities\":{},\"serverInfo\":{\"name\":\"fake\",\"version\":\"1\"}}}";
    }

    public static String method(ProtocolHttpRequest request) {
        JsonNode envelope = envelope(request);
        return envelope == null ? null : envelope.path("method").asText(null);
    }

    public static ProtocolHttpResponse exchange(ProtocolHttpRequest request, int status,
                                                Map<String, List<String>> headers, String body) {
        return new ProtocolHttpResponse(status, headers,
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)), request.uri());
    }

    private static long id(ProtocolHttpRequest request) {
        return envelope(request).path("id").asLong();
    }

    private static JsonNode envelope(ProtocolHttpRequest request) {
        if (!request.hasBody()) return null;
        try {
            return McpObjectMapper.INSTANCE.readTree(request.body());
        } catch (IOException invalid) {
            return null;
        }
    }
}
