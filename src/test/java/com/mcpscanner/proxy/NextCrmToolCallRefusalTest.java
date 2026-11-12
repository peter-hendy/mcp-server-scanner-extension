package com.mcpscanner.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.client.McpServerConfig;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.identity.IdentityRegistry;
import com.mcpscanner.identity.IdentityStore;
import com.mcpscanner.identity.ToolCallRefusals;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.mcp.ScannerSentinels;
import com.mcpscanner.testutil.InMemoryPersistedObject;
import com.mcpscanner.testutil.ProxyRequestStubs;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fi.iki.elonen.NanoHTTPD;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NextCrmToolCallRefusalTest {

    private static final String REFUSED_RESULT = "{\"content\":[{\"type\":\"text\",\"text\":"
            + "\"{\\\"error\\\":\\\"Unauthorized\\\",\\\"code\\\":\\\"UNAUTHORIZED\\\"}\"}],\"isError\":true}";

    private final HttpClient http = HttpClient.newHttpClient();
    private ToolCallRefusals refusals;
    private NextCrmServer server;
    private McpScannerSession session;
    private SseProxyServer proxy;

    @BeforeEach
    void connectWithoutCredentials() throws Exception {
        refusals = new ToolCallRefusals(new IdentityRegistry(
                new IdentityStore(InMemoryPersistedObject.configStore(), McpEventLog.noop())));
        server = new NextCrmServer();
        session = new McpScannerSession(http, new McpEventLog(null));
        session.connect(new McpServerConfig(server.endpoint(), TransportType.STREAMABLE_HTTP, new NoAuthStrategy()));
        proxy = TestSseProxies.builder(session).httpClient(http).toolCallReplies(refusals::replied).build();
    }

    @AfterEach
    void tearDown() {
        proxy.stop();
        session.disconnect();
        server.close();
    }

    @Test
    void aToolCallRefusedInsideA200ToolResultRaisesTheNoTokenSignal() throws Exception {
        NanoHTTPD.Response reply = proxy.serve(toolCall(managed(Map.of())));

        JsonNode body = McpObjectMapper.INSTANCE.readTree(reply.getData());
        assertThat(reply.getStatus().getRequestStatus()).isEqualTo(200);
        assertThat(body.path("result").path("isError").asBoolean()).isTrue();
        assertThat(refusals.refusedWithoutCredentials()).isTrue();
    }

    @Test
    void aToolCallSentAsAnotherIdentityLeavesTheSignalDown() throws Exception {
        proxy.serve(toolCall(managed(Map.of(ScannerSentinels.IDENTITY_HEADER, "bob"))));

        assertThat(refusals.refusedWithoutCredentials()).isFalse();
    }

    @Test
    void aManualToolCallFromRepeaterLeavesTheSignalDown() throws Exception {
        proxy.serve(toolCall(Map.of("Authorization", "Bearer expired")));

        assertThat(refusals.refusedWithoutCredentials()).isFalse();
    }

    private Map<String, String> managed(Map<String, String> baseline) {
        Map<String, String> headers = new HashMap<>(baseline);
        headers.putAll(proxy.captureScanConnection().headers(baseline));
        return headers;
    }

    private static NanoHTTPD.IHTTPSession toolCall(Map<String, String> headers) {
        NanoHTTPD.IHTTPSession request = mock(NanoHTTPD.IHTTPSession.class);
        when(request.getMethod()).thenReturn(NanoHTTPD.Method.POST);
        when(request.getUri()).thenReturn("/api/mcp");
        ProxyRequestStubs.stubBody(request, headers, "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"campaigns_get\",\"arguments\":{\"id\":\"c1\"}}}");
        return request;
    }

    private static final class NextCrmServer implements AutoCloseable {
        private final HttpServer server;

        private NextCrmServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/mcp", this::serve);
            server.start();
        }

        private String endpoint() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/mcp";
        }

        private void serve(HttpExchange exchange) throws IOException {
            try (exchange) {
                if (exchange.getRequestMethod().equals("DELETE")) {
                    reply(exchange, "application/json", "", 204);
                    return;
                }
                JsonNode request = McpObjectMapper.INSTANCE.readTree(exchange.getRequestBody());
                switch (request.path("method").asText()) {
                    case "initialize" -> initialized(exchange, request.path("id"));
                    case "tools/call" -> reply(exchange, "text/event-stream", "event: message\ndata: "
                            + "{\"jsonrpc\":\"2.0\",\"id\":" + request.path("id") + ",\"result\":" + REFUSED_RESULT
                            + "}\n\n", 200);
                    default -> reply(exchange, "application/json", "", 202);
                }
            }
        }

        private static void initialized(HttpExchange exchange, JsonNode id) throws IOException {
            exchange.getResponseHeaders().add("Mcp-Session-Id", "nextcrm-session");
            reply(exchange, "application/json", "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":"
                    + "{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{\"tools\":{}},"
                    + "\"serverInfo\":{\"name\":\"nextcrm\",\"version\":\"0.12.1\"}}}", 200);
        }

        private static void reply(HttpExchange exchange, String contentType, String body, int status)
                throws IOException {
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
}
