package com.mcpscanner.client;

import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.config.RedirectMode;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedirectLoggingInterceptorTest {

    @Test
    void sameOriginModeReplaysRedirectWithCredential() throws Exception {
        AtomicReference<String> receivedCredential = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/start", exchange -> {
            exchange.getResponseHeaders().set("Location", "/destination");
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        });
        server.createContext("/destination", exchange -> {
            receivedCredential.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/start"))
                    .header("Authorization", "Bearer test-secret")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();

            HttpResponse<String> response = new RedirectLoggingInterceptor(new McpEventLog(null),
                    () -> RedirectMode.SAME_ORIGIN)
                    .sendAndLog(client, request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(receivedCredential.get()).isEqualTo("Bearer test-secret");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void logsWarnWhenHostDiffers() throws Exception {
        CapturedWarnings warnings = new CapturedWarnings();
        McpEventLog eventLog = warnings.attach(new McpEventLog(null));

        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.uri()).thenReturn(URI.create("https://different.example/path"));
        doReturn(response).when(client).send(any(), any());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://original.example/path"))
                .GET()
                .build();

        new RedirectLoggingInterceptor(eventLog)
                .sendAndLog(client, request, HttpResponse.BodyHandlers.ofString());

        String warning = warnings.awaitFirst();
        assertThat(warning).contains("Cross-origin redirect followed");
        assertThat(warning).contains("original.example");
        assertThat(warning).contains("different.example");
    }

    @Test
    void logsWarnWhenSchemeDiffers() throws Exception {
        CapturedWarnings warnings = new CapturedWarnings();
        McpEventLog eventLog = warnings.attach(new McpEventLog(null));

        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.uri()).thenReturn(URI.create("http://same.example/path"));
        doReturn(response).when(client).send(any(), any());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://same.example/path"))
                .GET()
                .build();

        new RedirectLoggingInterceptor(eventLog)
                .sendAndLog(client, request, HttpResponse.BodyHandlers.ofString());

        assertThat(warnings.awaitFirst()).contains("Cross-origin redirect followed");
    }

    @Test
    void logsWarnWhenPortDiffers() throws Exception {
        CapturedWarnings warnings = new CapturedWarnings();
        McpEventLog eventLog = warnings.attach(new McpEventLog(null));

        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.uri()).thenReturn(URI.create("https://same.example:9443/path"));
        doReturn(response).when(client).send(any(), any());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://same.example:8443/path"))
                .GET()
                .build();

        new RedirectLoggingInterceptor(eventLog)
                .sendAndLog(client, request, HttpResponse.BodyHandlers.ofString());

        assertThat(warnings.awaitFirst()).contains("Cross-origin redirect followed");
    }

    @Test
    void doesNotLogWhenSameOrigin() throws Exception {
        CapturedWarnings warnings = new CapturedWarnings();
        McpEventLog eventLog = warnings.attach(new McpEventLog(null));

        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.uri()).thenReturn(URI.create("https://same.example/path"));
        doReturn(response).when(client).send(any(), any());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://same.example/path"))
                .GET()
                .build();

        new RedirectLoggingInterceptor(eventLog)
                .sendAndLog(client, request, HttpResponse.BodyHandlers.ofString());

        warnings.assertNoneArriveWithin(150);
    }

    @Test
    void doesNotLogWhenExplicitDefaultPortMatches() throws Exception {
        CapturedWarnings warnings = new CapturedWarnings();
        McpEventLog eventLog = warnings.attach(new McpEventLog(null));

        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.uri()).thenReturn(URI.create("https://example.com:443/foo"));
        doReturn(response).when(client).send(any(), any());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://example.com/foo"))
                .GET()
                .build();

        new RedirectLoggingInterceptor(eventLog)
                .sendAndLog(client, request, HttpResponse.BodyHandlers.ofString());

        warnings.assertNoneArriveWithin(150);
    }

    @Test
    void doesNotLogWhenImplicitHttpPortMatchesExplicit() throws Exception {
        CapturedWarnings warnings = new CapturedWarnings();
        McpEventLog eventLog = warnings.attach(new McpEventLog(null));

        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.uri()).thenReturn(URI.create("http://example.com/foo"));
        doReturn(response).when(client).send(any(), any());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://example.com:80/foo"))
                .GET()
                .build();

        new RedirectLoggingInterceptor(eventLog)
                .sendAndLog(client, request, HttpResponse.BodyHandlers.ofString());

        warnings.assertNoneArriveWithin(150);
    }

    @Test
    void returnsResponseUnchanged() throws Exception {
        McpEventLog eventLog = new McpEventLog(null);

        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.uri()).thenReturn(URI.create("https://same.example/path"));
        doReturn(response).when(client).send(any(), any());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://same.example/path"))
                .GET()
                .build();

        HttpResponse<String> got = new RedirectLoggingInterceptor(eventLog)
                .sendAndLog(client, request, HttpResponse.BodyHandlers.ofString());

        assertThat(got).isSameAs(response);
    }

    private static final class CapturedWarnings {
        private final CopyOnWriteArrayList<String> warnings = new CopyOnWriteArrayList<>();

        McpEventLog attach(McpEventLog log) {
            log.subscribe(entry -> {
                if (entry.level() == McpEventLog.Level.WARN) {
                    warnings.add(entry.message());
                }
            });
            return log;
        }

        String awaitFirst() {
            await().atMost(ofSeconds(2)).until(() -> !warnings.isEmpty());
            return warnings.get(0);
        }

        void assertNoneArriveWithin(long millis) {
            await().during(ofMillis(millis)).atMost(ofMillis(millis + 500)).until(() -> warnings.isEmpty());
            assertThat(warnings).isEmpty();
        }
    }
}
