package com.mcpscanner.core.connection;

import com.mcpscanner.core.protocol.*;
import java.util.*;

public final class ConnectionMatrixTest {
    public static void main(String[] args) {
        var fixtures = new MatrixCredentials();
        var providers = fixtures.rows();
        fixtures.rejectsMissingPrerequisites();
        int cases = 0;
        for (var provider : providers) {
            for (String version : List.of("2024-11-05", "2025-03-26", "2025-06-18", "2025-11-25", "2026-07-28")) {
                http(version, "fixture-session", provider.provider(), provider.headers());
                cases++;
                if (version.equals(McpRequests.MODERN_VERSION)) continue;
                http(version, null, provider.provider(), provider.headers());
                cases++;
            }
            sse(provider.provider(), provider.headers());
            cases++;
        }
        equal(60, cases);
        int negativeCases = new ConnectionMatrixFailures().verify(providers);
        System.out.println("ConnectionMatrixTest passed: " + cases + " protocol/auth combinations; " + negativeCases + " recovery/denial/negotiation cases; 3 prerequisite rejections");
    }

    private static void http(String version, String sessionId, CredentialProvider provider, Map<String, String> expectedCredentials) {
        var connection = new McpConnection(new McpFlow(8192, 10), provider);
        List<McpRequests.Request> sent = new ArrayList<>();
        McpFlow.HttpPort port = request -> {
            sent.add(request);
            assertCredentials(request.headers(), expectedCredentials);
            if (request.body().contains("server/discover")) {
                modernMetadata(request, "server/discover");
                return version.equals(McpRequests.MODERN_VERSION)
                    ? response(200, "{\"capabilities\":{},\"supportedVersions\":[\"2026-07-28\"]}")
                    : new McpFlow.HttpResponse(404, Map.of(), "", true);
            }
            if (request.body().contains("\"initialize\""))
                return new McpFlow.HttpResponse(200, sessionId == null ? Map.of() : Map.of("MCP-Session-Id", sessionId),
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"" + version + "\"}}", true);
            return response(200, "{}");
        };
        var session = connection.openHttp(port, "fixture", "1");
        equal(version, session.version());
        connection.send(port, session.operation("tools/call", "{\"name\":\"fixture\",\"arguments\":{}}", 9));
        var operation = sent.get(sent.size() - 1);
        equal(version, operation.headers().get("MCP-Protocol-Version"));
        if (version.equals(McpRequests.MODERN_VERSION)) {
            modernMetadata(operation, "tools/call");
            equal(2, sent.size());
            equal("tools/call", operation.headers().get("Mcp-Method"));
            equal("fixture", operation.headers().get("Mcp-Name"));
            equal(false, operation.headers().containsKey("MCP-Session-Id"));
        } else {
            equal(4, sent.size());
            equal(true, sent.get(1).body().contains("\"initialize\""));
            equal(true, sent.get(2).body().contains("notifications/initialized"));
            equal(version, sent.get(2).headers().get("MCP-Protocol-Version"));
            equal(sessionId, sent.get(2).headers().get("MCP-Session-Id"));
            equal(sessionId, operation.headers().get("MCP-Session-Id"));
            equal(false, operation.headers().containsKey("Mcp-Method"));
            equal(false, operation.body().contains("io.modelcontextprotocol/protocolVersion"));
        }
    }

    private static void sse(CredentialProvider provider, Map<String, String> expectedCredentials) {
        var connection = new McpConnection(new McpFlow(8192, 10), provider);
        var posts = new ArrayList<McpRequests.Request>();
        SseStreamFactory factory = headers -> {
            assertCredentials(headers, expectedCredentials);
            var events = new ArrayDeque<>(List.of("event: endpoint\ndata: /message\n\n",
                "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\"}}\n\n",
                "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":9,\"result\":{}}\n\n"));
            return new SseStreamFactory.Stream() {
                public String nextEvent() { return events.poll(); }
                public McpFlow.HttpResponse post(String endpoint, McpRequests.Request request) {
                    assertCredentials(request.headers(), expectedCredentials);
                    equal("/message", endpoint);
                    posts.add(request);
                    return new McpFlow.HttpResponse(202, Map.of(), "", true);
                }
                public void close() {}
            };
        };
        try (var session = connection.openSse(factory, "https://fixture.example/mcp", "fixture", "1")) {
            equal("2024-11-05", session.session().version());
            equal(9L, session.call(session.session().operation("tools/call", "{\"name\":\"fixture\",\"arguments\":{}}", 9), 9).id());
        }
        equal(3, posts.size());
        equal(true, posts.get(0).body().contains("\"protocolVersion\":\"2024-11-05\""));
        equal(true, posts.get(1).body().contains("notifications/initialized"));
        posts.forEach(request -> {
            equal("2024-11-05", request.headers().get("MCP-Protocol-Version"));
            equal(false, request.headers().containsKey("MCP-Session-Id"));
        });
    }

    private static void modernMetadata(McpRequests.Request request, String method) {
        equal(method, request.headers().get("Mcp-Method"));
        equal("2026-07-28", request.headers().get("MCP-Protocol-Version"));
        equal(true, request.body().contains("\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\""));
        equal(true, request.body().contains("\"io.modelcontextprotocol/clientInfo\":{\"name\":\"fixture\",\"version\":\"1\"}"));
        equal(true, request.body().contains("\"io.modelcontextprotocol/clientCapabilities\":{}"));
    }

    private static McpFlow.HttpResponse response(int status, String result) {
        return new McpFlow.HttpResponse(status, Map.of(), "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":" + result + "}", true);
    }

    private static void assertCredentials(Map<String, String> actual, Map<String, String> expected) {
        var headers = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll(actual);
        for (String name : List.of("Authorization", "X-Api-Key", "Cookie", "Proxy-Authorization"))
            equal(expected.get(name), headers.get(name));
    }

    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + " but was " + actual);
    }
}
