package com.mcpscanner.proxy;

import com.mcpscanner.testutil.ProxyRequestStubs;

import burp.api.montoya.logging.Logging;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.client.McpServerConfig;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fi.iki.elonen.NanoHTTPD;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HttpBatchReplyTest {
    private static final String BATCH = "[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"},"
            + "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"},"
            + "{\"jsonrpc\":\"2.0\",\"id\":\"two\",\"method\":\"tools/list\"}]";

    @Test
    void proxyCollectsSeparateSseRepliesForEachRewrittenBatchId() throws Exception {
        verifyBatch("2025-03-26", false);
    }

    @Test
    void proxyCollectsOnlyMatchingMembersOfAnSseReplyArray() throws Exception {
        verifyBatch("2025-03-26", true);
    }

    @Test
    void concurrentProxyBatchesKeepDistinctIdsAndReturnTheirOwnReplies() throws Exception {
        try (BatchServer server = new BatchServer("2025-03-26", true)) {
            HttpClient http = HttpClient.newHttpClient();
            Logging logging = mock(Logging.class);
            McpScannerSession managed = new McpScannerSession(http, new McpEventLog(null));
            managed.connect(new McpServerConfig(server.endpoint(), TransportType.STREAMABLE_HTTP,
                    new BearerTokenAuthStrategy("A")));
            SseProxyServer proxy = TestSseProxies.builder(managed).httpClient(http).build();
            try {
                Set<Long> replies = java.util.stream.IntStream.range(0, 8).parallel().mapToObj(index -> {
                    try {
                        var response = proxy.serve(request(Map.of("Authorization", "Bearer B")));
                        assertThat(response.getStatus().getRequestStatus()).isEqualTo(200);
                        JsonNode body = McpObjectMapper.INSTANCE.readTree(response.getData());
                        assertThat(body).hasSize(2);
                        return Set.of(body.get(0).path("id").asLong(), body.get(1).path("id").asLong());
                    } catch (Exception failure) {
                        throw new AssertionError(failure);
                    }
                }).flatMap(Set::stream).collect(java.util.stream.Collectors.toSet());
                assertThat(replies).hasSize(16).containsExactlyInAnyOrderElementsOf(server.ids);
            } finally {
                proxy.stop();
                managed.disconnect();
            }
        }
    }

    @Test
    void managedModernHttpRejectsBatchesBeforeSendingAnOperation() throws Exception {
        try (BatchServer server = new BatchServer("2026-07-28", true)) {
            HttpClient http = HttpClient.newHttpClient();
            Logging logging = mock(Logging.class);
            McpScannerSession managed = new McpScannerSession(http, new McpEventLog(null));
            managed.connect(new McpServerConfig(server.endpoint(), TransportType.MODERN_HTTP,
                    new BearerTokenAuthStrategy("A")));
            SseProxyServer proxy = TestSseProxies.builder(managed).httpClient(http).build();
            try {
                Map<String, String> headers = new HashMap<>();
                headers.putAll(proxy.auditHeaders(headers));
                assertThat(proxy.serve(request(headers)).getStatus().getRequestStatus()).isEqualTo(400);
                assertThat(server.ids).isEmpty();
            } finally {
                proxy.stop();
                managed.disconnect();
            }
        }
    }

    private void verifyBatch(String version, boolean arrayReply) throws Exception {
        try (BatchServer server = new BatchServer(version, arrayReply)) {
            HttpClient http = HttpClient.newHttpClient();
            Logging logging = mock(Logging.class);
            McpScannerSession managed = new McpScannerSession(http, new McpEventLog(null));
            managed.connect(new McpServerConfig(server.endpoint(), version.equals("2026-07-28")
                    ? TransportType.MODERN_HTTP : TransportType.STREAMABLE_HTTP, new BearerTokenAuthStrategy("A")));
            SseProxyServer proxy = TestSseProxies.builder(managed).httpClient(http).build();
            try {
                for (boolean audit : new boolean[]{false, true}) {
                    Map<String, String> headers = new HashMap<>();
                    if (audit) headers.putAll(proxy.auditHeaders(headers));
                    else headers.put("Authorization", "Bearer B");
                    var response = proxy.serve(request(headers));
                    assertThat(response.getStatus().getRequestStatus()).isEqualTo(200);
                    JsonNode replies = McpObjectMapper.INSTANCE.readTree(response.getData());
                    assertThat(replies).isNotNull();
                    assertThat(replies.isArray()).isTrue();
                    assertThat(replies).hasSize(2);
                    assertThat(replies.get(0).path("result").path("tools").get(0).path("name").asText()).isEqualTo("second");
                    assertThat(replies.get(1).path("result").path("tools").get(0).path("name").asText()).isEqualTo("first");
                    assertThat(replies.get(0).get("id")).isNotEqualTo(replies.get(1).get("id"));
                    for (JsonNode reply : replies) assertThat(server.ids).contains(reply.get("id").asLong());
                }
                assertThat(server.ids).hasSize(4);
            } finally {
                proxy.stop();
                managed.disconnect();
            }
        }
    }

    private NanoHTTPD.IHTTPSession request(Map<String, String> headers) throws Exception {
        var session = mock(NanoHTTPD.IHTTPSession.class);
        when(session.getMethod()).thenReturn(NanoHTTPD.Method.POST);
        when(session.getUri()).thenReturn("/mcp");
        ProxyRequestStubs.stubBody(session, headers, BATCH);
        return session;
    }

    private static final class BatchServer implements AutoCloseable {
        private final HttpServer server;
        private final String version;
        private final boolean arrayReply;
        private final Set<Long> ids = ConcurrentHashMap.newKeySet();

        private BatchServer(String version, boolean arrayReply) throws IOException {
            this.version = version;
            this.arrayReply = arrayReply;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/mcp", this::serve);
            server.start();
        }

        private String endpoint() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp"; }

        private void serve(HttpExchange exchange) throws IOException {
            try (exchange) {
                if (exchange.getRequestMethod().equals("DELETE")) { reply(exchange, "application/json", "", 204); return; }
                JsonNode request = McpObjectMapper.INSTANCE.readTree(exchange.getRequestBody());
                if (request.isArray()) {
                    long first = request.get(0).path("id").asLong();
                    long second = request.get(2).path("id").asLong();
                    ids.add(first);
                    ids.add(second);
                    String one = result(first, "first");
                    String two = result(second, "second");
                    String noise = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":" + first + ",\"method\":\"ping\"}\n\n"
                            + event(result(-99, "unrelated"));
                    reply(exchange, "text/event-stream", noise + (arrayReply
                            ? event("[" + two + "," + result(-99, "unrelated") + "," + one + "]")
                            : event(two) + event(two) + event(one)), 200);
                } else if (request.path("method").asText().equals("notifications/initialized")) {
                    reply(exchange, "application/json", "", 202);
                } else {
                    String result = version.equals("2026-07-28")
                            ? "{\"supportedVersions\":[\"" + version + "\"],\"capabilities\":{}}"
                            : "{\"protocolVersion\":\"" + version + "\",\"capabilities\":{},\"serverInfo\":{\"name\":\"fixture\",\"version\":\"1\"}}";
                    exchange.getResponseHeaders().add("Mcp-Session-Id", "fixture-session");
                    reply(exchange, "application/json", "{\"jsonrpc\":\"2.0\",\"id\":" + request.path("id") + ",\"result\":" + result + "}", 200);
                }
            }
        }

        private static String result(long id, String name) {
            return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"tools\":[{\"name\":\"" + name + "\"}]}}";
        }

        private static String event(String data) { return "event: message\ndata: " + data + "\n\n"; }

        private static void reply(HttpExchange exchange, String contentType, String body, int status) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", contentType);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            exchange.getResponseBody().write(bytes);
        }

        @Override public void close() { server.stop(0); }
    }
}
