package com.mcpscanner.testutil;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public final class AuthenticatedMcpServer implements AutoCloseable {
    public static final String TOOL_NAME = "fixture_tool";
    private static final String ACCESS_TOKEN_PREFIX = "access-";
    private static final String BEARER_PREFIX = "Bearer " + ACCESS_TOKEN_PREFIX;

    public record McpCall(String httpMethod, String rpcMethod, Map<String, String> headers, int status) {
        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    public record TokenCall(Map<String, String> form, String authorization) {
        public String grantType() {
            return form.get("grant_type");
        }
    }

    private final HttpServer server;
    private final List<McpCall> mcpCalls = Collections.synchronizedList(new ArrayList<>());
    private final List<TokenCall> tokenCalls = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger issuedTokens = new AtomicInteger();
    private volatile int revokedThrough;
    private volatile long tokenLifetimeSeconds = 3600;
    private volatile boolean denyTokenRequests;
    private volatile List<String> tokenEndpointAuthMethods = List.of("client_secret_basic", "none");
    private volatile Predicate<Map<String, String>> admission = headers -> true;
    private volatile boolean oauthMetadataWithheld;
    private volatile String customChallenge;

    private AuthenticatedMcpServer(HttpServer server) {
        this.server = server;
    }

    public static AuthenticatedMcpServer start() throws IOException {
        AuthenticatedMcpServer fixture = new AuthenticatedMcpServer(
                HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0));
        fixture.registerContexts();
        fixture.server.start();
        return fixture;
    }

    public String origin() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public String endpoint() {
        return origin() + "/mcp";
    }

    public String sseEndpoint() {
        return origin() + "/sse";
    }

    public void requireHeaders(Map<String, String> expected) {
        admission = headers -> expected.entrySet().stream()
                .allMatch(entry -> entry.getValue().equals(headers.get(entry.getKey().toLowerCase(Locale.ROOT))));
    }

    public void requireIssuedAccessToken() {
        admission = headers -> isLiveAccessToken(headers.get("authorization"));
    }

    public void requireUnobtainableCredentials() {
        admission = headers -> false;
    }

    public void withholdOAuthMetadata() {
        oauthMetadataWithheld = true;
        customChallenge = "Bearer realm=\"fixture\"";
    }

    public void challengeWith(String wwwAuthenticate) {
        customChallenge = wwwAuthenticate;
    }

    public void revokeIssuedAccessTokens() {
        revokedThrough = issuedTokens.get();
    }

    public void issueTokensValidFor(long seconds) {
        tokenLifetimeSeconds = seconds;
    }

    public void advertiseTokenEndpointAuthMethods(List<String> methods) {
        tokenEndpointAuthMethods = List.copyOf(methods);
    }

    public void denyTokenRequests() {
        denyTokenRequests = true;
    }

    public List<McpCall> mcpCalls() {
        synchronized (mcpCalls) {
            return List.copyOf(mcpCalls);
        }
    }

    public List<TokenCall> tokenCalls() {
        synchronized (tokenCalls) {
            return List.copyOf(tokenCalls);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void registerContexts() {
        server.createContext("/mcp", this::handleMcp);
        server.createContext("/sse", this::handleSse);
        server.createContext("/.well-known/oauth-protected-resource", exchange -> sendPathScopedProtectedResourceMetadata(exchange));
        server.createContext("/.well-known/oauth-authorization-server", exchange -> sendMetadata(exchange, authorizationServerMetadata()));
        server.createContext("/token", this::handleToken);
        server.createContext("/register", exchange -> sendJson(exchange, 201, Map.of("client_id", "dcr-client")));
    }

    private boolean isLiveAccessToken(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return false;
        }
        int serial = Integer.parseInt(authorization.substring(BEARER_PREFIX.length()));
        return serial > revokedThrough && serial <= issuedTokens.get();
    }

    private void handleMcp(HttpExchange exchange) throws IOException {
        Map<String, String> headers = lowerCaseHeaders(exchange);
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonNode request = body.isBlank() ? McpObjectMapper.INSTANCE.createObjectNode() : McpObjectMapper.INSTANCE.readTree(body);
        String rpcMethod = request.path("method").asText(null);
        if (!admission.test(headers)) {
            mcpCalls.add(new McpCall(exchange.getRequestMethod(), rpcMethod, headers, 401));
            sendChallenge(exchange);
            return;
        }
        int status = mcpStatus(exchange.getRequestMethod(), request);
        mcpCalls.add(new McpCall(exchange.getRequestMethod(), rpcMethod, headers, status));
        respondToMcp(exchange, request, rpcMethod, status);
    }

    private void handleSse(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            sendEmpty(exchange, 405);
            return;
        }
        if (!admission.test(lowerCaseHeaders(exchange))) {
            sendChallenge(exchange);
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        OutputStream stream = exchange.getResponseBody();
        stream.write("event: endpoint\ndata: /mcp\n\n".getBytes(StandardCharsets.UTF_8));
        stream.flush();
    }

    private static int mcpStatus(String httpMethod, JsonNode request) {
        if (!httpMethod.equals("POST")) {
            return 405;
        }
        return request.has("id") ? 200 : 202;
    }

    private void respondToMcp(HttpExchange exchange, JsonNode request, String rpcMethod, int status) throws IOException {
        if (status != 200) {
            sendEmpty(exchange, status);
            return;
        }
        sendJson(exchange, 200, Map.of("jsonrpc", "2.0", "id", request.get("id"), "result", resultFor(rpcMethod)));
    }

    private static Object resultFor(String rpcMethod) {
        return switch (rpcMethod) {
            case "server/discover" -> Map.of("supportedVersions", List.of("2026-07-28"), "capabilities", Map.of("tools", Map.of()));
            case "initialize" -> Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of("tools", Map.of()),
                    "serverInfo", Map.of("name", "authenticated-fixture", "version", "1"));
            case "tools/list" -> Map.of("tools", List.of(Map.of("name", TOOL_NAME, "inputSchema", Map.of("type", "object"))));
            default -> Map.of();
        };
    }

    private void sendPathScopedProtectedResourceMetadata(HttpExchange exchange) throws IOException {
        if (exchange.getRequestURI().getPath().equals("/.well-known/oauth-protected-resource")) {
            sendEmpty(exchange, 404);
            return;
        }
        sendMetadata(exchange, protectedResourceMetadata(exchange));
    }

    private void sendMetadata(HttpExchange exchange, Object metadata) throws IOException {
        if (oauthMetadataWithheld) {
            sendEmpty(exchange, 404);
            return;
        }
        sendJson(exchange, 200, metadata);
    }

    private void sendChallenge(HttpExchange exchange) throws IOException {
        String challenge = customChallenge != null ? customChallenge
                : "Bearer resource_metadata=\"" + origin() + "/.well-known/oauth-protected-resource"
                        + exchange.getRequestURI().getPath() + "\"";
        exchange.getResponseHeaders().add("WWW-Authenticate", challenge);
        sendEmpty(exchange, 401);
    }

    private void handleToken(HttpExchange exchange) throws IOException {
        Map<String, String> form = parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        tokenCalls.add(new TokenCall(form, exchange.getRequestHeaders().getFirst("Authorization")));
        if (denyTokenRequests) {
            sendJson(exchange, 400, Map.of("error", "invalid_client"));
            return;
        }
        int serial = issuedTokens.incrementAndGet();
        sendJson(exchange, 200, Map.of("access_token", ACCESS_TOKEN_PREFIX + serial, "token_type", "Bearer",
                "expires_in", tokenLifetimeSeconds, "refresh_token", "refresh-" + serial));
    }

    private Map<String, Object> protectedResourceMetadata(HttpExchange exchange) {
        String suffix = exchange.getRequestURI().getPath().substring("/.well-known/oauth-protected-resource".length());
        return Map.of("resource", origin() + suffix, "authorization_servers", List.of(origin()));
    }

    private Map<String, Object> authorizationServerMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("issuer", origin());
        metadata.put("authorization_endpoint", origin() + "/authorize");
        metadata.put("token_endpoint", origin() + "/token");
        metadata.put("registration_endpoint", origin() + "/register");
        metadata.put("response_types_supported", List.of("code"));
        metadata.put("code_challenge_methods_supported", List.of("S256"));
        metadata.put("client_id_metadata_document_supported", true);
        metadata.put("grant_types_supported", List.of("authorization_code", "refresh_token", "client_credentials",
                "urn:ietf:params:oauth:grant-type:jwt-bearer"));
        metadata.put("authorization_grant_profiles_supported", List.of("urn:ietf:params:oauth:grant-profile:id-jag"));
        metadata.put("token_endpoint_auth_methods_supported", tokenEndpointAuthMethods);
        metadata.put("token_endpoint_auth_signing_alg_values_supported", List.of("RS256"));
        return metadata;
    }

    private static Map<String, String> lowerCaseHeaders(HttpExchange exchange) {
        Map<String, String> headers = new HashMap<>();
        exchange.getRequestHeaders().forEach((name, values) ->
                headers.put(name.toLowerCase(Locale.ROOT), values.get(0)));
        return headers;
    }

    private static Map<String, String> parseForm(String form) {
        Map<String, String> values = new HashMap<>();
        for (String pair : form.split("&")) {
            int separator = pair.indexOf('=');
            if (separator > 0) {
                values.put(decode(pair.substring(0, separator)), decode(pair.substring(separator + 1)));
            }
        }
        return values;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static int sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = McpObjectMapper.INSTANCE.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
        return status;
    }

    private static int sendEmpty(HttpExchange exchange, int status) {
        try {
            exchange.sendResponseHeaders(status, -1);
            return status;
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        } finally {
            exchange.close();
        }
    }
}
