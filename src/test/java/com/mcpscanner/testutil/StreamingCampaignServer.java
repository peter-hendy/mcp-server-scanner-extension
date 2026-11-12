package com.mcpscanner.testutil;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class StreamingCampaignServer implements AutoCloseable {

    public static final String ALICE_TOKEN = "alice-token";
    public static final String BOB_TOKEN = "bob-token";
    public static final String ALICE_CAMPAIGN = "c-alice-0001";
    public static final String BOB_CAMPAIGN = "c-bob-0002";
    public static final String ALICE_MARKER = "ALICE_ONLY_CAMPAIGN_MARKER";
    public static final String BOB_MARKER = "BOB_ONLY_CAMPAIGN_MARKER";
    public static final String TOOL = "campaigns_get";

    private static final Map<String, String> USERS = Map.of("Bearer " + ALICE_TOKEN, "alice",
            "Bearer " + BOB_TOKEN, "bob");
    private static final Map<String, Campaign> CAMPAIGNS = Map.of(
            ALICE_CAMPAIGN, new Campaign("alice", ALICE_MARKER),
            BOB_CAMPAIGN, new Campaign("bob", BOB_MARKER));
    private static final String TOOLS = "{\"tools\":[{\"name\":\"" + TOOL + "\",\"annotations\":{\"readOnlyHint\":true},"
            + "\"inputSchema\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}},"
            + "\"required\":[\"id\"]}}]}";

    private static final String SESSION_HEADER = "Mcp-Session-Id";

    private final boolean ownerScoped;
    private final boolean keepsSessions;
    private final Streamed streamed;
    private final HttpServer server;
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final Set<String> liveSessions = ConcurrentHashMap.newKeySet();
    private final List<String> issuedSessions = new CopyOnWriteArrayList<>();
    private final List<String> deletedSessions = new CopyOnWriteArrayList<>();

    private record Campaign(String owner, String marker) {
    }

    private enum Streamed {
        EVERY_REPLY, TOOL_CALLS, NOTHING;

        boolean streams(String method) {
            return this == EVERY_REPLY || this == TOOL_CALLS && "tools/call".equals(method);
        }
    }

    private StreamingCampaignServer(boolean ownerScoped, boolean keepsSessions, Streamed streamed) throws IOException {
        this.ownerScoped = ownerScoped;
        this.keepsSessions = keepsSessions;
        this.streamed = streamed;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::serve);
        server.start();
    }

    public static StreamingCampaignServer vulnerable() throws IOException {
        return new StreamingCampaignServer(false, false, Streamed.EVERY_REPLY);
    }

    public static StreamingCampaignServer ownerScoped() throws IOException {
        return new StreamingCampaignServer(true, false, Streamed.EVERY_REPLY);
    }

    public static StreamingCampaignServer sessionKeeping() throws IOException {
        return new StreamingCampaignServer(false, true, Streamed.EVERY_REPLY);
    }

    public static StreamingCampaignServer streamingOnlyToolCalls() throws IOException {
        return new StreamingCampaignServer(false, true, Streamed.TOOL_CALLS);
    }

    public static StreamingCampaignServer streamingNothing() throws IOException {
        return new StreamingCampaignServer(false, true, Streamed.NOTHING);
    }

    public List<String> issuedSessions() {
        return List.copyOf(issuedSessions);
    }

    public List<String> deletedSessions() {
        return List.copyOf(deletedSessions);
    }

    public String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
    }

    public List<String> toolCalls() {
        return List.copyOf(calls);
    }

    private void serve(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (keepsSessions && "DELETE".equals(exchange.getRequestMethod())) {
                delete(exchange);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod()) || !"/mcp".equals(exchange.getRequestURI().getPath())) {
                reply(exchange, 404, "application/json", "");
                return;
            }
            JsonNode request = McpObjectMapper.INSTANCE.readTree(exchange.getRequestBody());
            if (keepsSessions && !inLiveSession(exchange, request)) {
                reply(exchange, 404, "application/json", "");
                return;
            }
            if (!request.has("id")) {
                reply(exchange, 202, "application/json", "");
                return;
            }
            answer(exchange, request);
        }
    }

    private void answer(HttpExchange exchange, JsonNode request) throws IOException {
        String message = "{\"jsonrpc\":\"2.0\",\"id\":" + request.path("id") + ","
                + resultFor(request, caller(exchange)) + "}";
        if (streamed.streams(request.path("method").asText())) {
            reply(exchange, 200, "text/event-stream", "event: message\ndata: " + message + "\n\n");
        } else {
            reply(exchange, 200, "application/json", message);
        }
    }

    private boolean inLiveSession(HttpExchange exchange, JsonNode request) {
        if ("initialize".equals(request.path("method").asText())) {
            String session = UUID.randomUUID().toString();
            liveSessions.add(session);
            issuedSessions.add(session);
            exchange.getResponseHeaders().add(SESSION_HEADER, session);
            return true;
        }
        return liveSessions.contains(exchange.getRequestHeaders().getFirst(SESSION_HEADER));
    }

    private void delete(HttpExchange exchange) throws IOException {
        String session = exchange.getRequestHeaders().getFirst(SESSION_HEADER);
        boolean deleted = session != null && liveSessions.remove(session);
        if (deleted) {
            deletedSessions.add(session);
        }
        reply(exchange, deleted ? 200 : 404, "application/json", "");
    }

    private String resultFor(JsonNode request, Optional<String> caller) {
        return switch (request.path("method").asText()) {
            case "initialize" -> "\"result\":{\"protocolVersion\":"
                    + request.path("params").path("protocolVersion") + ",\"capabilities\":{\"tools\":{}},"
                    + "\"serverInfo\":{\"name\":\"streaming-campaigns\",\"version\":\"1\"}}";
            case "tools/list" -> "\"result\":" + TOOLS;
            case "tools/call" -> "\"result\":" + toolResult(request.path("params"), caller);
            case "ping" -> "\"result\":{}";
            default -> "\"error\":{\"code\":-32601,\"message\":\"Method not found\"}";
        };
    }

    private String toolResult(JsonNode params, Optional<String> caller) {
        String id = params.path("arguments").path("id").asText();
        calls.add(caller.orElse("anonymous") + ":" + params.path("name").asText() + ":" + id);
        if (caller.isEmpty()) {
            return refusal("UNAUTHORIZED");
        }
        return Optional.ofNullable(CAMPAIGNS.get(id))
                .filter(campaign -> !ownerScoped || campaign.owner().equals(caller.get()))
                .map(campaign -> text("{\\\"id\\\":\\\"" + id + "\\\",\\\"name\\\":\\\"" + campaign.marker() + "\\\"}",
                        false))
                .orElseGet(() -> refusal("NOT_FOUND"));
    }

    private static String refusal(String code) {
        return text("{\\\"error\\\":\\\"" + code + "\\\"}", true);
    }

    private static String text(String escapedText, boolean isError) {
        return "{\"content\":[{\"type\":\"text\",\"text\":\"" + escapedText + "\"}],\"isError\":" + isError + "}";
    }

    private static Optional<String> caller(HttpExchange exchange) {
        return Optional.ofNullable(exchange.getRequestHeaders().getFirst("Authorization")).map(USERS::get);
    }

    private static void reply(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
