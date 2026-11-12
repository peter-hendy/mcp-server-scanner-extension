package com.mcpscanner.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class McpFixtureServer implements AutoCloseable {
    static final String MODERN_VERSION = "2026-07-28";
    private static final String SESSION_HEADER = "Mcp-Session-Id";
    private static final String VERSION_HEADER = "MCP-Protocol-Version";
    private static final String METHOD_HEADER = "Mcp-Method";
    private static final String TOOL_NAME = "echo";
    private static final String SERVER_NAME = "version-fixture";
    private static final java.util.Set<String> PINGED_METHODS = java.util.Set.of("tools/list", "tools/call");
    static final String TOOL_RESULT_TEXT = "echoed by fixture";

    record ObservedCall(String channel, String method, String protocolVersion, String sessionId,
                        int sessionHeaderCount, boolean scannerCapabilityReceived, JsonNode params,
                        String authorization) {}

    private static final InetSocketAddress LOOPBACK = new InetSocketAddress("127.0.0.1", 0);

    private final McpFixtureProfile profile;
    private final HttpServer server;
    private final String scheme;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final FixtureSseStreams streams = new FixtureSseStreams();
    private final List<ObservedCall> observed = new CopyOnWriteArrayList<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<String> sessionDeleteAuthorizations = new CopyOnWriteArrayList<>();
    private final Map<String, String> toolsGrantedByAuthorization = new java.util.concurrent.ConcurrentHashMap<>();
    private final FixturePings pings = new FixturePings();
    private final java.util.concurrent.atomic.AtomicInteger sseStreamsOpened = new java.util.concurrent.atomic.AtomicInteger();
    private volatile String negotiatedHttpVersion;

    public McpFixtureServer(McpFixtureProfile profile) throws IOException {
        this(profile, HttpServer.create(LOOPBACK, 0), "http");
    }

    private McpFixtureServer(McpFixtureProfile profile, HttpServer server, String scheme) {
        this.profile = profile;
        this.server = server;
        this.scheme = scheme;
        server.setExecutor(executor);
        server.createContext("/mcp", this::handleEndpoint);
        server.createContext("/messages", this::handleSseMessage);
        server.createContext("/", this::handleUnknownRoute);
        server.start();
    }

    static McpFixtureServer overHttps(McpFixtureProfile profile, HttpsConfigurator tls) throws IOException {
        HttpsServer server = HttpsServer.create(LOOPBACK, 0);
        server.setHttpsConfigurator(tls);
        return new McpFixtureServer(profile, server, "https");
    }

    static String toolName() {
        return TOOL_NAME;
    }

    static String serverName() {
        return SERVER_NAME;
    }

    public String endpoint() {
        return scheme + "://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
    }

    void pingOpenStreams(int count) throws IOException {
        for (int ping = 0; ping < count; ping++) {
            streams.broadcast("message", "{\"jsonrpc\":\"2.0\",\"id\":\"idle-" + ping + "\",\"method\":\"ping\"}");
        }
    }

    int sseStreamsOpened() {
        return sseStreamsOpened.get();
    }

    List<FixturePings.PingReply> pingReplies() {
        return pings.replies();
    }

    void grantToolTo(String authorization, String toolName) {
        toolsGrantedByAuthorization.put(authorization, toolName);
    }

    List<String> sessionDeleteAuthorizations() {
        return List.copyOf(sessionDeleteAuthorizations);
    }

    public List<String> requests() {
        return List.copyOf(requests);
    }

    public void forgetRequests() {
        requests.clear();
    }

    List<ObservedCall> callsTo(String method) {
        return observed.stream().filter(call -> call.method().equals(method)).toList();
    }

    private void handleUnknownRoute(HttpExchange exchange) throws IOException {
        requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
        respondStatus(exchange, 404);
    }

    private void handleEndpoint(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
        }
        switch (exchange.getRequestMethod()) {
            case "GET" -> openSseStream(exchange);
            case "POST" -> handleHttpPost(exchange);
            case "DELETE" -> {
                sessionDeleteAuthorizations.add(String.valueOf(header(exchange, "Authorization")));
                respondStatus(exchange, 202);
            }
            default -> respondStatus(exchange, 405);
        }
    }

    private void openSseStream(HttpExchange exchange) throws IOException {
        if (!profile.servesSse()) {
            respondStatus(exchange, 405);
            return;
        }
        String streamId = streams.open(exchange);
        sseStreamsOpened.incrementAndGet();
        streams.send(streamId, "endpoint", "/messages?session=" + streamId);
    }

    private void handleHttpPost(HttpExchange exchange) throws IOException {
        JsonNode body = readBody(exchange);
        logPost(exchange, body);
        if (FixturePings.isClientReply(body)) {
            acceptPingReply(exchange, "http", body);
            return;
        }
        record(exchange, "http", body);
        if (exchange.getRequestHeaders().containsKey(METHOD_HEADER)) {
            handleModern(exchange, body);
        } else if (!profile.servesStreamableHttp()) {
            respondStatus(exchange, 405);
        } else {
            handleStreamable(exchange, body);
        }
    }

    private void handleModern(HttpExchange exchange, JsonNode body) throws IOException {
        String method = body.path("method").asText();
        if (!profile.modern()) {
            respondHttp(exchange, 400, error(body, -32600, "Bad Request: No valid session ID provided", null));
        } else if (method.equals("server/discover")) {
            respondHttp(exchange, 200, result(body, discoverResult()));
        } else if (!MODERN_VERSION.equals(header(exchange, VERSION_HEADER))) {
            respondHttp(exchange, 400, error(body, -32600, "Missing protocol version", null));
        } else {
            respondCatalog(exchange, body);
        }
    }

    private void handleStreamable(HttpExchange exchange, JsonNode body) throws IOException {
        if (exchange.getRequestHeaders().getOrDefault(SESSION_HEADER, List.of()).size() > 1) {
            exchange.close();
            return;
        }
        String method = body.path("method").asText();
        if (method.equals("initialize")) {
            handleInitialize(exchange, body);
        } else if (method.startsWith("notifications/")) {
            respondStatus(exchange, 202);
        } else if (!matchesNegotiatedSession(exchange)) {
            respondHttp(exchange, 400, error(body, -32600, "Bad Request: session or version mismatch", null));
        } else {
            respondCatalog(exchange, body);
        }
    }

    private void handleInitialize(HttpExchange exchange, JsonNode body) throws IOException {
        String offered = body.path("params").path("protocolVersion").asText();
        Optional<String> accepted = profile.acceptedVersion(offered);
        if (accepted.isEmpty()) {
            respondHttp(exchange, 400, error(body, -32602, "Unsupported protocol version",
                    Map.of("supportedVersions", profile.httpVersions(), "requested", offered)));
            return;
        }
        negotiatedHttpVersion = accepted.get();
        exchange.getResponseHeaders().set(SESSION_HEADER, sessionIdFor(negotiatedHttpVersion));
        respondHttp(exchange, 200, result(body, initializeResult(negotiatedHttpVersion)));
    }

    private boolean matchesNegotiatedSession(HttpExchange exchange) {
        String version = negotiatedHttpVersion;
        return version != null
                && sessionIdFor(version).equals(header(exchange, SESSION_HEADER))
                && version.equals(header(exchange, VERSION_HEADER));
    }

    private void handleSseMessage(HttpExchange exchange) throws IOException {
        JsonNode body = readBody(exchange);
        logPost(exchange, body);
        if (FixturePings.isClientReply(body)) {
            acceptPingReply(exchange, "sse", body);
            return;
        }
        record(exchange, "sse", body);
        String streamId = exchange.getRequestURI().getQuery().substring("session=".length());
        String method = body.path("method").asText();
        if (method.equals("initialize")) {
            streams.send(streamId, "message", result(body, initializeResult(profile.sseVersion())));
        } else if (!carriesRequiredSseVersion(exchange)) {
            respondStatus(exchange, 400);
            return;
        } else if (pingsBefore(body)) {
            String authorization = header(exchange, "Authorization");
            executor.execute(() -> pingThenReplyOnSse(streamId, body, authorization));
        } else if (!method.startsWith("notifications/")) {
            streams.send(streamId, "message", catalogReply(body, header(exchange, "Authorization")));
        }
        respondStatus(exchange, 202);
    }

    private void acceptPingReply(HttpExchange exchange, String channel, JsonNode body) throws IOException {
        pings.accept(new FixturePings.PingReply(channel, body, header(exchange, VERSION_HEADER),
                header(exchange, SESSION_HEADER), header(exchange, "Authorization")));
        respondStatus(exchange, 202);
    }

    private boolean pingsBefore(JsonNode body) {
        return profile.pingsBeforeReply() > 0 && PINGED_METHODS.contains(body.path("method").asText());
    }

    private void respondCatalog(HttpExchange exchange, JsonNode body) throws IOException {
        if (!pingsBefore(body)) {
            respondHttp(exchange, 200, catalogReply(body, header(exchange, "Authorization")));
            return;
        }
        String streamId = streams.open(exchange);
        if (pings.pingAndAwaitReply(json -> streams.send(streamId, "message", json), profile.pingsBeforeReply())) {
            streams.send(streamId, "message", catalogReply(body, header(exchange, "Authorization")));
        } else {
            streams.close(streamId);
        }
    }

    private void pingThenReplyOnSse(String streamId, JsonNode body, String authorization) {
        try {
            if (pings.pingAndAwaitReply(json -> streams.send(streamId, "message", json), profile.pingsBeforeReply())) {
                streams.send(streamId, "message", catalogReply(body, authorization));
            }
        } catch (IOException streamClosed) {
            streams.close(streamId);
        }
    }

    private boolean carriesRequiredSseVersion(HttpExchange exchange) {
        return !profile.sseVersionHeaderRequired() || profile.sseVersion().equals(header(exchange, VERSION_HEADER));
    }

    private void logPost(HttpExchange exchange, JsonNode body) {
        String method = body.path("method").asText(FixturePings.isClientReply(body) ? "(reply)" : "(none)");
        requests.add("POST " + exchange.getRequestURI().getPath() + " " + method);
    }

    private String catalogReply(JsonNode body, String authorization) throws IOException {
        Optional<String> grantedTool = Optional.ofNullable(authorization).map(toolsGrantedByAuthorization::get);
        Map<String, Object> catalog = profile.toolPages().paged() && body.path("method").asText().equals("tools/list")
                ? toolsPage(body.path("params").path("cursor").asText(null))
                : catalogResult(body.path("method").asText(), grantedTool);
        return catalog == null ? error(body, -32601, "Method not found", null) : result(body, catalog);
    }

    private static Map<String, Object> catalogResult(String method, Optional<String> grantedTool) {
        return switch (method) {
            case "tools/list" -> Map.of("tools", java.util.stream.Stream.concat(java.util.stream.Stream.of(TOOL_NAME), grantedTool.stream())
                    .map(name -> Map.of("name", name, "inputSchema", Map.of("type", "object", "properties", Map.of())))
                    .toList());
            case "resources/list" -> Map.of("resources", List.of());
            case "resources/templates/list" -> Map.of("resourceTemplates", List.of());
            case "prompts/list" -> Map.of("prompts", List.of());
            case "tools/call" -> Map.of("content", List.of(Map.of("type", "text", "text", TOOL_RESULT_TEXT)));
            case "completion/complete" -> Map.of("completion", Map.of("values", List.of("python")));
            default -> null;
        };
    }

    private Map<String, Object> toolsPage(String cursor) {
        McpFixtureProfile.ToolPages pages = profile.toolPages();
        int page = cursor == null ? 0 : Integer.parseInt(cursor.substring("page-".length()));
        int first = page * pages.pageSize();
        int end = Math.min(first + pages.pageSize(), pages.tools());
        List<Map<String, Object>> tools = java.util.stream.IntStream.range(first, end)
                .mapToObj(index -> Map.<String, Object>of("name", index == 0 ? TOOL_NAME : "tool-" + index,
                        "inputSchema", Map.of("type", "object", "properties", Map.of())))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", tools);
        if (end < pages.tools()) {
            result.put("nextCursor", "page-" + (page + 1));
        }
        return result;
    }

    private static Map<String, Object> initializeResult(String version) {
        return Map.of("protocolVersion", version, "capabilities", Map.of("tools", Map.of()),
                "serverInfo", serverInfo());
    }

    private static Map<String, Object> discoverResult() {
        return Map.of("supportedVersions", List.of(MODERN_VERSION), "capabilities", Map.of("tools", Map.of()),
                "_meta", Map.of("io.modelcontextprotocol/serverInfo", serverInfo()));
    }

    private static Map<String, Object> serverInfo() {
        return Map.of("name", SERVER_NAME, "version", "1");
    }

    private static String sessionIdFor(String version) {
        return "session-" + version;
    }

    private static String result(JsonNode request, Map<String, Object> result) throws IOException {
        return envelope(request, "result", result);
    }

    private static String error(JsonNode request, int code, String message, Map<String, Object> data)
            throws IOException {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        if (data != null) {
            error.put("data", data);
        }
        return envelope(request, "error", error);
    }

    private static String envelope(JsonNode request, String outcome, Map<String, Object> payload)
            throws IOException {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("jsonrpc", "2.0");
        envelope.put("id", request.path("id").asLong());
        envelope.put(outcome, payload);
        return McpObjectMapper.INSTANCE.writeValueAsString(envelope);
    }

    private void record(HttpExchange exchange, String channel, JsonNode body) {
        observed.add(new ObservedCall(channel, body.path("method").asText(),
                header(exchange, VERSION_HEADER), header(exchange, SESSION_HEADER),
                exchange.getRequestHeaders().getOrDefault(SESSION_HEADER, List.of()).size(),
                exchange.getRequestHeaders().containsKey("X-Mcp-Scanner-Audit-Capability"), body.path("params"),
                header(exchange, "Authorization")));
    }

    private static String header(HttpExchange exchange, String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    private static JsonNode readBody(HttpExchange exchange) throws IOException {
        return McpObjectMapper.INSTANCE.readTree(exchange.getRequestBody());
    }

    private void respondHttp(HttpExchange exchange, int status, String json) throws IOException {
        if (profile.framing() == McpFixtureProfile.ReplyFraming.EVENT_STREAM) {
            respond(exchange, status, "text/event-stream", "event: message\ndata: " + json + "\n\n");
        } else {
            respond(exchange, status, "application/json", json);
        }
    }

    private static void respondStatus(HttpExchange exchange, int status) throws IOException {
        respond(exchange, status, "application/json", "");
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    @Override
    public void close() {
        streams.close();
        server.stop(0);
        executor.shutdownNow();
    }
}
