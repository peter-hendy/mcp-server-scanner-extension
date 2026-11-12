package com.mcpscanner.client;

import com.mcpscanner.logging.McpEventLog;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SameHostRedirectFollowingTest {
    private static final String PAYLOAD = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";

    private final HttpClient neverFollowing = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final RedirectLoggingInterceptor interceptor = new RedirectLoggingInterceptor(new McpEventLog(null));
    private final AtomicInteger hopsServed = new AtomicInteger();
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/final", exchange -> {
            String received = exchange.getRequestMethod() + " " + new String(
                    exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            reply(exchange, 200, received);
        });
        server.createContext("/loop", exchange -> {
            hopsServed.incrementAndGet();
            redirect(exchange, 307, "/loop");
        });
        server.createContext("/elsewhere", exchange -> redirect(exchange, 307,
                "http://localhost:" + server.getAddress().getPort() + "/final"));
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @ParameterizedTest
    @ValueSource(ints = {307, 308})
    void methodAndBodyAreReplayedToTheSameHostTarget(int status) throws Exception {
        server.createContext("/moved" + status, exchange -> redirect(exchange, status, "/final"));

        HttpResponse<String> response = post("/moved" + status);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("POST " + PAYLOAD);
    }

    @Test
    void redirectToAnotherHostIsNotFollowed() throws Exception {
        HttpResponse<String> response = post("/elsewhere");

        assertThat(response.statusCode()).isEqualTo(307);
    }

    @Test
    void redirectLoopIsBounded() throws Exception {
        HttpResponse<String> response = post("/loop");

        assertThat(response.statusCode()).isEqualTo(307);
        assertThat(hopsServed.get()).isLessThanOrEqualTo(10);
    }

    private HttpResponse<String> post(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        "http://127.0.0.1:" + server.getAddress().getPort() + path))
                .POST(HttpRequest.BodyPublishers.ofString(PAYLOAD)).build();
        return interceptor.sendAndLog(neverFollowing, request, HttpResponse.BodyHandlers.ofString());
    }

    private static void redirect(com.sun.net.httpserver.HttpExchange exchange, int status, String location)
            throws IOException {
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
