package com.mcpscanner.core.connection;

import com.mcpscanner.core.protocol.*;
import java.util.*;

final class ConnectionMatrixFailures {
    private final List<String> httpVersions = List.of("2024-11-05", "2025-03-26", "2025-06-18", "2025-11-25", "2026-07-28");

    int verify(List<MatrixCredentials.Row> rows) {
        int cases = 0;
        for (var row : rows) {
            for (boolean counteroffer : List.of(false, true)) {
                unsupportedHttpVersion(row, counteroffer);
                cases++;
            }
            for (String version : httpVersions) {
                supportedCounteroffer(row, version);
                cases++;
                for (int status : List.of(401, 403)) {
                    anonymousHttp(row, version, status);
                    cases++;
                }
                if (!version.equals(McpRequests.MODERN_VERSION)) {
                    expiredSessionPreservesIdentity(row, version);
                    cases++;
                }
            }
            for (int status : List.of(401, 403)) {
                anonymousSse(row, status);
                cases++;
            }
            unsupportedSseVersion(row);
            cases++;
        }
        equal(144, cases);
        return cases;
    }

    private void unsupportedHttpVersion(MatrixCredentials.Row row, boolean counteroffer) {
        var initializations = new int[1];
        var probes = new int[1];
        var connection = connection(row.provider());
        rejects(() -> connection.openHttp(request -> {
            credentials(request.headers(), row.headers());
            if (request.body().contains("server/discover")) {
                probes[0]++;
                return counteroffer ? counteroffer(-32022, "2099-01-01") : response(404, "");
            }
            equal(true, request.body().contains("\"initialize\""));
            initializations[0]++;
            return counteroffer ? counteroffer(-32602, "2099-01-01")
                    : initialized("2099-01-01", "unsupported");
        }, "fixture", "1"));
        equal(1, initializations[0]);
        equal(1, probes[0]);
    }

    private void supportedCounteroffer(MatrixCredentials.Row row, String version) {
        var offers = new int[1];
        var initializations = new int[1];
        var modern = version.equals(McpRequests.MODERN_VERSION);
        var connection = connection(row.provider());
        McpFlow.HttpPort port = request -> {
            credentials(request.headers(), row.headers());
            if (request.body().contains("server/discover")) {
                if (!modern) return response(404, "");
                return ++offers[0] == 1 ? counteroffer(-32022, version) : discovery();
            }
            if (request.body().contains("\"initialize\"")) {
                if (++initializations[0] == 1) return counteroffer(-32602, version);
                equal(true, request.body().contains("\"protocolVersion\":\"" + version + "\""));
                equal(version, request.headers().get("MCP-Protocol-Version"));
                return initialized(version, "counter-session");
            }
            return response(202, "");
        };
        if (version.equals(McpRequests.HANDSHAKE_VERSION)) {
            rejects(() -> connection.openHttp(port, "fixture", "1"));
            equal(1, initializations[0]);
        } else {
            var session = connection.openHttp(port, "fixture", "1");
            equal(version, session.version());
            equal(modern ? 0 : 2, initializations[0]);
        }
        equal(modern ? 2 : 0, offers[0]);
    }

    private void anonymousHttp(MatrixCredentials.Row row, String version, int status) {
        var connection = anonymous(row);
        var operations = new int[1];
        McpFlow.HttpPort port = request -> {
            credentials(request.headers(), Map.of());
            if (request.body().contains("server/discover"))
                return version.equals(McpRequests.MODERN_VERSION) ? discovery() : response(404, "");
            if (request.body().contains("\"initialize\"")) return initialized(version, "anonymous-session");
            if (request.body().contains("notifications/initialized")) return response(202, "");
            operations[0]++;
            return challenge(status);
        };
        var session = connection.openHttp(port, "fixture", "1");
        equal(version, session.version());
        var request = session.operation("tools/call", "{\"name\":\"fixture\",\"arguments\":{}}", 9);
        equal(status, connection.send(port, staleCredentials(request, row)).status());
        equal(1, operations[0]);
    }

    private void anonymousSse(MatrixCredentials.Row row, int status) {
        var opens = new int[1];
        var operations = new int[1];
        SseStreamFactory factory = headers -> {
            credentials(headers, Map.of());
            opens[0]++;
            var events = sseEvents("2024-11-05");
            return new SseStreamFactory.Stream() {
                public String nextEvent() { return events.poll(); }
                public McpFlow.HttpResponse post(String endpoint, McpRequests.Request request) {
                    credentials(request.headers(), Map.of());
                    if (request.body().contains("tools/call")) { operations[0]++; return challenge(status); }
                    return response(202, "");
                }
                public void close() {}
            };
        };
        try (var session = anonymous(row).openSse(factory, "https://fixture.example/mcp", "fixture", "1")) {
            var request = session.session().operation("tools/call", "{\"name\":\"fixture\",\"arguments\":{}}", 9);
            try { session.call(staleCredentials(request, row), 9); throw new AssertionError("Anonymous challenge must fail"); }
            catch (SseOpenException expected) { equal(status, expected.response().status()); }
        }
        equal(1, opens[0]);
        equal(1, operations[0]);
    }

    private void unsupportedSseVersion(MatrixCredentials.Row row) {
        var posts = new int[1];
        var closed = new int[1];
        SseStreamFactory factory = headers -> {
            credentials(headers, row.headers());
            var events = sseEvents("2099-01-01");
            return new SseStreamFactory.Stream() {
                public String nextEvent() { return events.poll(); }
                public McpFlow.HttpResponse post(String endpoint, McpRequests.Request request) {
                    credentials(request.headers(), row.headers());
                    posts[0]++;
                    return response(202, "");
                }
                public void close() { closed[0]++; }
            };
        };
        rejects(() -> connection(row.provider()).openSse(factory, "https://fixture.example/mcp", "fixture", "1"));
        equal(1, posts[0]);
        equal(1, closed[0]);
    }

    private void expiredSessionPreservesIdentity(MatrixCredentials.Row row, String version) {
        var handshakes = new int[1];
        var operations = new ArrayList<String>();
        var session = connection(row.provider()).openHttpSession(request -> {
            credentials(request.headers(), row.headers());
            if (request.body().contains("server/discover")) return response(404, "");
            if (request.body().contains("\"initialize\"")) return initialized(version, "session-" + ++handshakes[0]);
            if (request.body().contains("notifications/initialized")) return response(202, "");
            equal(version, request.headers().get("MCP-Protocol-Version"));
            operations.add(request.headers().get("MCP-Session-Id"));
            return response(operations.size() == 1 ? 404 : 200, "");
        }, "fixture", "1");
        equal(200, session.send(session.session().operation("tools/call", "{\"name\":\"fixture\",\"arguments\":{}}", 9)).status());
        equal(List.of("session-1", "session-2"), operations);
        equal(version, session.session().version());
        equal(2, handshakes[0]);
    }

    private McpConnection anonymous(MatrixCredentials.Row row) {
        return connection(new CredentialProvider() {
            public Map<String, String> headers() { throw new AssertionError("Anonymous context accessed source credentials"); }
            public Set<String> headerNames() { return row.provider().headerNames(); }
            public OperationRecovery beginOperation(String operation) { throw new AssertionError("Anonymous context attempted recovery"); }
        }).anonymous();
    }

    private McpRequests.Request staleCredentials(McpRequests.Request request, MatrixCredentials.Row row) {
        var headers = new HashMap<>(request.headers());
        headers.putAll(row.headers());
        headers.put("Cookie", "stale-cookie");
        headers.put("Proxy-Authorization", "Basic stale-proxy");
        return new McpRequests.Request(request.body(), Map.copyOf(headers));
    }

    private McpConnection connection(CredentialProvider provider) { return new McpConnection(new McpFlow(8192, 10), provider); }

    private ArrayDeque<String> sseEvents(String version) {
        return new ArrayDeque<>(List.of("event: endpoint\ndata: /message\n\n",
                "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"" + version + "\"}}\n\n"));
    }

    private McpFlow.HttpResponse challenge(int status) {
        return new McpFlow.HttpResponse(status, Map.of("WWW-Authenticate", status == 401 ? "Bearer error=\"invalid_token\""
                : "Bearer error=\"insufficient_scope\", scope=\"write\""), "", true);
    }

    private McpFlow.HttpResponse discovery() {
        return response(200, "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"capabilities\":{},\"supportedVersions\":[\"2026-07-28\"]}}");
    }

    private McpFlow.HttpResponse counteroffer(int code, String version) {
        return response(400, "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":" + code
                + ",\"data\":{\"supportedVersions\":[\"" + version + "\"]}}}");
    }

    private McpFlow.HttpResponse initialized(String version, String session) {
        return new McpFlow.HttpResponse(200, Map.of("MCP-Session-Id", session),
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"" + version + "\"}}", true);
    }

    private McpFlow.HttpResponse response(int status, String body) { return new McpFlow.HttpResponse(status, Map.of(), body, true); }

    private void rejects(Runnable operation) {
        try { operation.run(); throw new AssertionError("Unsupported protocol must reject"); }
        catch (IllegalArgumentException expected) {}
    }

    private void credentials(Map<String, String> actual, Map<String, String> expected) {
        var headers = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll(actual);
        for (String name : List.of("Authorization", "X-Api-Key", "Cookie", "Proxy-Authorization"))
            equal(expected.get(name), headers.get(name));
    }

    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + " but was " + actual);
    }
}
