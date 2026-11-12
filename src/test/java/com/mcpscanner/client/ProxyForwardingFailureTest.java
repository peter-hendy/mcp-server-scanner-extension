package com.mcpscanner.client;

import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.proxy.SseProxyServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ProxyForwardingFailureTest {

    private static final String SESSION_ID = "session-1";
    private static final String READ = "{\"jsonrpc\":\"2.0\",\"id\":17,\"method\":\"resources/read\","
            + "\"params\":{\"uri\":\"ui://widget/site-builder.html\"}}";

    private HttpServer upstream;
    private SseProxyServer proxy;
    private McpScannerSession session;
    private final CountDownLatch upstreamReleased = new CountDownLatch(1);

    @AfterEach
    void stop() {
        upstreamReleased.countDown();
        if (proxy != null) proxy.stop();
        if (session != null) session.disconnect();
        if (upstream != null) upstream.stop(0);
    }

    @Test
    void oversizedUpstreamReplyNamesTheLimitInTheProxyError() throws Exception {
        startUpstream(Duration.ZERO, "x".repeat(9 * 1024 * 1024));
        connect(Duration.ofSeconds(10));

        HttpResponse<String> response = sendThroughProxy();

        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(response.body()).contains("exceeded");
    }

    @Test
    void slowUpstreamReplyNamesTheTimeoutInTheProxyError() throws Exception {
        startUpstream(Duration.ofMillis(1500), "ok");
        connect(Duration.ofSeconds(1));

        HttpResponse<String> response = sendThroughProxy();

        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(response.body()).contains("no response within 1 s");
    }

    @Test
    void replySlowerThanTheOldTenSecondCapStillSucceedsWithinTheRequestTimeout() throws Exception {
        startUpstream(Duration.ofMillis(1500), "ok");
        connect(Duration.ofSeconds(5));

        HttpResponse<String> response = sendThroughProxy();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("site-builder");
    }

    @Test
    void defaultRequestTimeoutLeavesRoomForSlowRealWorldTools() {
        assertThat(McpScannerSession.DEFAULT_REQUEST_TIMEOUT).isGreaterThanOrEqualTo(Duration.ofSeconds(60));
    }

    private void startUpstream(Duration readDelay, String resourceText) throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/mcp", exchange -> answer(exchange, readDelay, resourceText));
        upstream.start();
    }

    private void answer(HttpExchange exchange, Duration readDelay, String resourceText) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("Mcp-Session-Id", SESSION_ID);
        if (body.contains("notifications/initialized")) {
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
            return;
        }
        String reply;
        if (body.contains("\"initialize\"")) {
            reply = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-06-18\","
                    + "\"capabilities\":{\"resources\":{}},\"serverInfo\":{\"name\":\"f\",\"version\":\"1\"}}}";
        } else {
            holdReply(readDelay);
            reply = "{\"jsonrpc\":\"2.0\",\"id\":17,\"result\":{\"contents\":[{\"uri\":\"ui://widget/site-builder.html\","
                    + "\"text\":\"" + resourceText + "\"}]}}";
        }
        byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        } catch (IOException ignored) {
        }
    }

    private void holdReply(Duration delay) {
        try {
            upstreamReleased.await(delay.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void connect(Duration requestTimeout) throws IOException {
        session = new McpScannerSession(HttpClient.newHttpClient(), new McpEventLog(null),
                () -> true, requestTimeout);
        session.connect(new McpServerConfig("http://127.0.0.1:" + upstream.getAddress().getPort() + "/mcp",
                TransportType.STREAMABLE_HTTP, new NoAuthStrategy()));
        proxy = com.mcpscanner.proxy.TestSseProxies.builder(session).build();
        proxy.start();
    }

    private HttpResponse<String> sendThroughProxy() throws Exception {
        Map<String, String> baseline = Map.of("Mcp-Session-Id", SESSION_ID, "MCP-Protocol-Version", "2025-06-18");
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + proxy.port() + "/mcp"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(READ));
        baseline.forEach(request::header);
        proxy.auditHeaders(baseline).forEach(request::header);
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
