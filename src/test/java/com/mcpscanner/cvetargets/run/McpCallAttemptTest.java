package com.mcpscanner.cvetargets.run;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.cvetargets.manifest.SeedRequest;
import com.mcpscanner.cvetargets.requests.ArmEndpoint;
import com.mcpscanner.mcp.McpObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class McpCallAttemptTest {

    private static final String SESSION = "session-7";
    private static final String TOKEN_REJECTED = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"isError\":true,"
            + "\"content\":[{\"type\":\"text\",\"text\":\"{\\\"error\\\":\\\"Invalid token\\\"}\"}]}}";
    private static final String CAMPAIGN = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":"
            + "[{\"type\":\"text\",\"text\":\"campaign\"}]}}";

    private final List<String> callHeaders = new CopyOnWriteArrayList<>();
    private volatile boolean tokensInserted;
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", this::answer);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void isNotReadyWhileTheServerRejectsTheArmCredential() {
        assertThat(attempt().notReadyBecause(endpoint())).hasValueSatisfying(reason ->
                assertThat(reason).contains("Invalid token"));
    }

    @Test
    void isReadyOnceTheCallSucceedsWithTheArmCredentialInTheIssuedSession() {
        tokensInserted = true;

        assertThat(attempt().notReadyBecause(endpoint())).isEmpty();
        assertThat(callHeaders).containsExactly("Bearer alice " + SESSION);
    }

    @Test
    void isNotReadyWhileTheServerIsDown() {
        server.stop(0);

        assertThat(attempt().notReadyBecause(endpoint())).isPresent();
    }

    private static McpCallAttempt attempt() {
        ObjectNode params = McpObjectMapper.INSTANCE.createObjectNode().put("name", "campaigns_get");
        return new McpCallAttempt(new SeedRequest("tools/call", params));
    }

    private ArmEndpoint endpoint() {
        return new ArmEndpoint(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/mcp"),
                new BearerTokenAuthStrategy("alice"), TransportType.AUTO);
    }

    private void answer(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.contains("\"initialize\"")) {
            exchange.getResponseHeaders().add("Mcp-Session-Id", SESSION);
            reply(exchange, 200, "{\"jsonrpc\":\"2.0\",\"id\":0,\"result\":{}}");
        } else if (body.contains("notifications/initialized")) {
            reply(exchange, 202, "");
        } else {
            callHeaders.add(exchange.getRequestHeaders().getFirst("Authorization") + " "
                    + exchange.getRequestHeaders().getFirst("Mcp-Session-Id"));
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            reply(exchange, 200, "event: message\ndata: " + (tokensInserted ? CAMPAIGN : TOKEN_REJECTED) + "\n\n");
        }
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
