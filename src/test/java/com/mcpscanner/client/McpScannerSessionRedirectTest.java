package com.mcpscanner.client;

import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.logging.McpEventLog;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class McpScannerSessionRedirectTest {
    private static final String DISCOVER_REPLY =
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"capabilities\":{},\"supportedVersions\":[\"2026-07-28\"]}}";

    private HttpServer server;

    @BeforeEach
    void startRedirectingServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Location", "/v2/mcp");
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        });
        server.createContext("/v2/mcp", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = DISCOVER_REPLY.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void scannerTargetsTheEndpointReachedAfterA307() {
        McpScannerSession session = new McpScannerSession(
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build(), new McpEventLog(null));

        session.connect(new McpServerConfig(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp",
                TransportType.MODERN_HTTP, new NoAuthStrategy()));

        assertThat(session.resolvedEndpoint()).endsWith("/v2/mcp");
    }
}
