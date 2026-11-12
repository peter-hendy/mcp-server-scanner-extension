package com.mcpscanner.client;

import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.config.RedirectMode;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectPolicyIntegrationTest {

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private HttpServer source;
    private HttpServer destination;

    @AfterEach
    void stopServers() {
        if (source != null) source.stop(0);
        if (destination != null) destination.stop(0);
    }

    @Test
    void offDoesNotForwardCredentialToRedirectTarget() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        startServers(captured);

        HttpResponse<String> response = send(RedirectMode.METHOD_PRESERVING_SAME_ORIGIN);

        assertThat(response.statusCode()).isEqualTo(307);
        assertThat(captured.get()).isNull();
    }

    @Test
    void sameOriginDoesNotForwardCredentialToDifferentPort() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        startServers(captured);

        HttpResponse<String> response = send(RedirectMode.SAME_ORIGIN);

        assertThat(response.statusCode()).isEqualTo(307);
        assertThat(captured.get()).isNull();
    }

    @Test
    void alwaysForwardsCredentialToDifferentPort() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        startServers(captured);

        HttpResponse<String> response = send(RedirectMode.ALWAYS);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(captured.get()).isEqualTo("Bearer test-secret");
    }

    @Test
    void redirectLoopStopsAfterFiveHops() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        source = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        source.createContext("/loop", exchange -> {
            requests.incrementAndGet();
            exchange.getResponseHeaders().set("Location", "/loop");
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        });
        source.start();
        HttpRequest request = request("/loop");

        HttpResponse<String> response = new RedirectLoggingInterceptor(new McpEventLog(null),
                () -> RedirectMode.SAME_ORIGIN)
                .sendAndLog(client, request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(307);
        assertThat(requests.get()).isEqualTo(6);
    }

    private HttpResponse<String> send(RedirectMode mode) throws Exception {
        return new RedirectLoggingInterceptor(new McpEventLog(null), () -> mode)
                .sendAndLog(client, request("/start"), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                + source.getAddress().getPort() + path))
                .header("Authorization", "Bearer test-secret")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
    }

    private void startServers(AtomicReference<String> captured) throws IOException {
        destination = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        destination.createContext("/capture", exchange -> {
            captured.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        destination.start();
        source = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        source.createContext("/start", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:"
                    + destination.getAddress().getPort() + "/capture");
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        });
        source.start();
    }
}
