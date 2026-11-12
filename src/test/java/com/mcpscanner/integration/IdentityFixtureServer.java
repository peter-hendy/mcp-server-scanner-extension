package com.mcpscanner.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

final class IdentityFixtureServer implements AutoCloseable {

    static final String MARKER = "ALICE_PRIVATE_SESSION_MARKER";
    private static final String LEGACY = "2024-11-05";
    private static final Map<String, String> TOKENS = Map.of("Bearer alice-token", "alice",
            "Bearer bob-token", "bob", "Bearer harry-token", "harry");
    private static final String TOOLS = "{\"tools\":["
            + "{\"name\":\"seed_private_record\",\"inputSchema\":{\"type\":\"object\"}},"
            + "{\"name\":\"read_session_record\",\"inputSchema\":{\"type\":\"object\"}},"
            + "{\"name\":\"admin_report\",\"inputSchema\":{\"type\":\"object\"}}]}";

    private final boolean stateful;
    private final boolean sessionsBound;
    private final HttpServer server;
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicInteger issued = new AtomicInteger();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> records = new ConcurrentHashMap<>();
    final List<String> deleted = new CopyOnWriteArrayList<>();
    final List<String> operations = new CopyOnWriteArrayList<>();
    final List<String> requests = new CopyOnWriteArrayList<>();

    private record Session(String principal, String version) {
    }

    private IdentityFixtureServer(boolean stateful, boolean sessionsBound) throws IOException {
        this.stateful = stateful;
        this.sessionsBound = sessionsBound;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", this::serve);
        server.start();
    }

    static IdentityFixtureServer statefulSessions(boolean sessionsBound) throws IOException {
        return new IdentityFixtureServer(true, sessionsBound);
    }

    static IdentityFixtureServer statelessVersionScoped() throws IOException {
        return new IdentityFixtureServer(false, false);
    }

    String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
    }

    private void serve(HttpExchange exchange) throws IOException {
        try (exchange) {
            String sessionId = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
            if (exchange.getRequestMethod().equals("DELETE")) {
                requests.add("DELETE /mcp");
                deleted.add(sessionId);
                sessions.remove(sessionId);
                reply(exchange, 204, "", null);
                return;
            }
            String principal = TOKENS.get(exchange.getRequestHeaders().getFirst("Authorization"));
            JsonNode request = json.readTree(exchange.getRequestBody());
            String method = request.path("method").asText();
            requests.add(exchange.getRequestMethod() + " /mcp " + method + " as " + principal);
            if (principal == null) {
                reply(exchange, 401, "{\"error\":\"unauthorized\"}", null);
                return;
            }
            if (method.equals("initialize")) {
                initialize(exchange, request, principal);
                return;
            }
            if (!request.has("id")) {
                reply(exchange, 202, "", null);
                return;
            }
            Optional<Session> session = Optional.ofNullable(sessionId).map(sessions::get);
            if (stateful && session.isEmpty()) {
                reply(exchange, 404, "{\"error\":\"Session not found\"}", null);
                return;
            }
            if (stateful && sessionsBound && !session.get().principal().equals(principal)) {
                reply(exchange, 404, "{\"error\":\"Session not found\"}", null);
                return;
            }
            String effective = session.map(Session::principal).orElse(principal);
            String version = session.map(Session::version)
                    .orElse(exchange.getRequestHeaders().getFirst("MCP-Protocol-Version"));
            String tool = request.path("params").path("name").asText();
            if (method.equals("tools/call")) {
                operations.add(principal + ":" + tool + "@" + version + (sessionId == null ? "" : "#" + sessionId));
            }
            reply(exchange, 200, "{\"jsonrpc\":\"2.0\",\"id\":" + request.get("id") + ",\"result\":"
                    + result(method, tool, effective, sessionId, version) + "}", null);
        }
    }

    private void initialize(HttpExchange exchange, JsonNode request, String principal) throws IOException {
        String version = request.path("params").path("protocolVersion").asText();
        String sessionId = stateful ? "s-" + issued.incrementAndGet() : null;
        if (sessionId != null) {
            sessions.put(sessionId, new Session(principal, version));
        }
        reply(exchange, 200, "{\"jsonrpc\":\"2.0\",\"id\":" + request.get("id") + ",\"result\":{\"protocolVersion\":\""
                + version + "\",\"capabilities\":{\"tools\":{}},\"serverInfo\":{\"name\":\"identity\",\"version\":\"1\"}}}",
                sessionId);
    }

    private String result(String method, String tool, String principal, String sessionId, String version) {
        if (method.equals("tools/list")) {
            return TOOLS;
        }
        if (!method.equals("tools/call")) {
            return "{}";
        }
        return switch (tool) {
            case "seed_private_record" -> seed(principal, sessionId);
            case "read_session_record" -> text("session_key=" + sessionId + " principal=" + principal
                    + " record=" + records.getOrDefault(String.valueOf(sessionId), "none"));
            case "admin_report" -> principal.equals("bob") && !LEGACY.equals(version)
                    ? "{\"isError\":true,\"content\":[{\"type\":\"text\",\"text\":\"insufficient scopes\"}]}"
                    : text("ADMIN-EFFECT report");
            default -> "{\"isError\":true,\"content\":[{\"type\":\"text\",\"text\":\"unknown tool\"}]}";
        };
    }

    private String seed(String principal, String sessionId) {
        if (principal.equals("alice") && sessionId != null) {
            records.put(sessionId, MARKER);
        }
        return text("seeded " + sessionId);
    }

    private static String text(String value) {
        return "{\"content\":[{\"type\":\"text\",\"text\":\"" + value + "\"}]}";
    }

    private static void reply(HttpExchange exchange, int status, String body, String sessionId) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        if (sessionId != null) {
            exchange.getResponseHeaders().set("Mcp-Session-Id", sessionId);
        }
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
