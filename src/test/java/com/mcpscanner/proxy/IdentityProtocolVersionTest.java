package com.mcpscanner.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.client.McpServerConfig;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.ScannerSentinels;
import com.mcpscanner.testutil.ProxyRequestStubs;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fi.iki.elonen.NanoHTTPD;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdentityProtocolVersionTest {

    private static final String TOOL_CALL =
            "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\",\"params\":{\"name\":\"admin\",\"arguments\":{}}}";

    @Test
    void sessionlessIdentityProbeIsHandshakedAndSentAtItsOwnSupportedVersion() throws Exception {
        try (VersionRecordingServer server = new VersionRecordingServer()) {
            HttpClient http = HttpClient.newHttpClient();
            McpScannerSession managed = new McpScannerSession(http, new McpEventLog(null));
            managed.connect(new McpServerConfig(server.endpoint(), TransportType.STREAMABLE_HTTP,
                    new BearerTokenAuthStrategy("A")));
            SseProxyServer proxy = TestSseProxies.builder(managed).httpClient(http).build();
            try {
                var response = proxy.serve(request(Map.of("Authorization", "Bearer B",
                        ScannerSentinels.IDENTITY_HEADER, "reader", "MCP-Protocol-Version", "2024-11-05")));

                assertThat(response.getStatus().getRequestStatus()).isEqualTo(200);
                assertThat(server.initializations).last().isEqualTo("Bearer B@2024-11-05");
                assertThat(server.operations).containsExactly("Bearer B@2024-11-05");
            } finally {
                proxy.stop();
                managed.disconnect();
            }
        }
    }

    @Test
    void sessionlessIdentityProbeWithAnUnsupportedVersionUsesTheNegotiatedOne() throws Exception {
        try (VersionRecordingServer server = new VersionRecordingServer()) {
            HttpClient http = HttpClient.newHttpClient();
            McpScannerSession managed = new McpScannerSession(http, new McpEventLog(null));
            managed.connect(new McpServerConfig(server.endpoint(), TransportType.STREAMABLE_HTTP,
                    new BearerTokenAuthStrategy("A")));
            String negotiated = managed.negotiatedProtocolVersion();
            SseProxyServer proxy = TestSseProxies.builder(managed).httpClient(http).build();
            try {
                proxy.serve(request(Map.of("Authorization", "Bearer B", ScannerSentinels.IDENTITY_HEADER, "reader",
                        "MCP-Protocol-Version", "2099-01-01")));

                assertThat(server.operations).containsExactly("Bearer B@" + negotiated);
            } finally {
                proxy.stop();
                managed.disconnect();
            }
        }
    }

    private static NanoHTTPD.IHTTPSession request(Map<String, String> headers) throws Exception {
        var request = mock(NanoHTTPD.IHTTPSession.class);
        when(request.getMethod()).thenReturn(NanoHTTPD.Method.POST);
        when(request.getUri()).thenReturn("/mcp");
        ProxyRequestStubs.stubBody(request, headers, TOOL_CALL);
        return request;
    }

    private static final class VersionRecordingServer implements AutoCloseable {
        private final HttpServer server;
        private final ObjectMapper json = new ObjectMapper();
        private final AtomicInteger sessions = new AtomicInteger();
        private final List<String> initializations = new CopyOnWriteArrayList<>();
        private final List<String> operations = new CopyOnWriteArrayList<>();

        private VersionRecordingServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/mcp", this::serve);
            server.start();
        }

        private String endpoint() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
        }

        private void serve(HttpExchange exchange) throws IOException {
            try (exchange) {
                if (exchange.getRequestMethod().equals("DELETE")) {
                    reply(exchange, 204, "", null);
                    return;
                }
                JsonNode request = json.readTree(exchange.getRequestBody());
                String caller = exchange.getRequestHeaders().getFirst("Authorization");
                String method = request.path("method").asText();
                if (method.equals("initialize")) {
                    String version = request.path("params").path("protocolVersion").asText();
                    initializations.add(caller + "@" + version);
                    reply(exchange, 200, "{\"jsonrpc\":\"2.0\",\"id\":" + request.get("id") + ",\"result\":{"
                            + "\"protocolVersion\":\"" + version + "\",\"capabilities\":{\"tools\":{}},"
                            + "\"serverInfo\":{\"name\":\"versions\",\"version\":\"1\"}}}", "s-" + sessions.incrementAndGet());
                    return;
                }
                if (!request.has("id")) {
                    reply(exchange, 202, "", null);
                    return;
                }
                if (method.equals("tools/call")) {
                    operations.add(caller + "@" + exchange.getRequestHeaders().getFirst("MCP-Protocol-Version"));
                }
                reply(exchange, 200, "{\"jsonrpc\":\"2.0\",\"id\":" + request.get("id")
                        + ",\"result\":{\"tools\":[],\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}}", null);
            }
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
}
