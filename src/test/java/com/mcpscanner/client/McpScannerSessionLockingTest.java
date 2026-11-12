package com.mcpscanner.client;

import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.logging.McpEventLog;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.mcpscanner.testutil.AwaitedFutures.doneWithin;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpScannerSessionLockingTest {

    private static final String ENDPOINT = "http://127.0.0.1/mcp";
    private static final String ADMISSION =
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"capabilities\":{},\"supportedVersions\":[\"2026-07-28\"]}}";

    @Test
    void readersAndDisconnectStayResponsiveWhileConnectHandshakeIsStalled() throws Exception {
        HttpClient client = mock(HttpClient.class);
        AtomicBoolean handshakeStarted = new AtomicBoolean();
        CompletableFuture<Void> releaseHandshake = new CompletableFuture<>();
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            handshakeStarted.set(true);
            releaseHandshake.get(10, SECONDS);
            return response(ADMISSION);
        });
        McpScannerSession session = session(client);
        CompletableFuture<Void> connect = CompletableFuture.runAsync(() -> session.connect(config()));
        try {
            await().atMost(2, SECONDS).pollInterval(10, MILLISECONDS).untilTrue(handshakeStarted);

            assertThat(promptly(session::transportType)).isNull();
            assertThat(promptly(session::connectionGeneration)).isPositive();
            assertThat(promptly(session::protocolHeaders)).isEmpty();
            doneWithin(CompletableFuture.runAsync(session::disconnect), Duration.ofSeconds(1)).join();
            assertThat(session.resolvedEndpoint()).isNull();
        } finally {
            releaseHandshake.complete(null);
            doneWithin(connect, Duration.ofSeconds(5));
        }
        assertThat(connect).isCompletedExceptionally();
        assertThat(session.transportType()).isNull();
    }

    @Test
    void stalledScannerRequestDoesNotHoldTheSessionMonitor() throws Exception {
        HttpClient client = mock(HttpClient.class);
        AtomicInteger sends = new AtomicInteger();
        AtomicBoolean requestStarted = new AtomicBoolean();
        CompletableFuture<Void> releaseRequest = new CompletableFuture<>();
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            if (sends.getAndIncrement() == 0) {
                return response(ADMISSION);
            }
            requestStarted.set(true);
            releaseRequest.get(10, SECONDS);
            return response("{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{}}");
        });
        McpScannerSession session = session(client);
        session.connect(config());
        CompletableFuture<?> stalled = CompletableFuture.runAsync(() -> session.sendHttpOperation(
                session.operation("tools/list", "{}", 7)));
        try {
            await().atMost(2, SECONDS).pollInterval(10, MILLISECONDS).untilTrue(requestStarted);

            assertThat(promptly(session::transportType)).isEqualTo(TransportType.MODERN_HTTP);
            assertThat(promptly(session::scannerHeaders)).isNotEmpty();
            assertThat(promptly(() -> session.operation("tools/list", "{}", 8))).isNotNull();
        } finally {
            releaseRequest.complete(null);
            doneWithin(stalled, Duration.ofSeconds(5));
        }
        session.disconnect();
    }

    @Test
    void sessionDeleteClosesTheResponseBodyWithoutReadingIt() throws Exception {
        HttpClient client = mock(HttpClient.class);
        AtomicInteger posts = new AtomicInteger();
        TrackingBody deleteBody = new TrackingBody();
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            if (request.method().equals("DELETE")) {
                return response(200, deleteBody, Map.of());
            }
            if (request.headers().firstValue("Mcp-Method").isPresent()) {
                return response(404, body(""), Map.of());
            }
            return posts.getAndIncrement() == 0
                    ? response(200, body(LEGACY_INITIALIZE), Map.of("Mcp-Session-Id", List.of("session-1")))
                    : response(202, body(""), Map.of());
        });
        McpScannerSession session = session(client);
        session.connect(new McpServerConfig(ENDPOINT, TransportType.STREAMABLE_HTTP, new NoAuthStrategy()));

        session.disconnect();

        assertThat(deleteBody.closed).isTrue();
        assertThat(deleteBody.reads).hasValue(0);
    }

    private static <T> T promptly(Supplier<T> reader) {
        return doneWithin(CompletableFuture.supplyAsync(reader), Duration.ofSeconds(1)).join();
    }

    private static final String LEGACY_INITIALIZE = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":"
            + "\"2025-06-18\",\"capabilities\":{},\"serverInfo\":{\"name\":\"t\",\"version\":\"1\"}}}";

    private static InputStream body(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<InputStream> response(int status, InputStream body, Map<String, List<String>> extra) {
        Map<String, List<String>> headers = new java.util.HashMap<>(extra);
        headers.put("Content-Type", List.of("application/json"));
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(HttpHeaders.of(headers, (name, value) -> true));
        when(response.body()).thenReturn(body);
        return response;
    }

    private static final class TrackingBody extends InputStream {
        private final AtomicInteger reads = new AtomicInteger();
        private volatile boolean closed;

        @Override
        public int read() {
            reads.incrementAndGet();
            return 'x';
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static McpScannerSession session(HttpClient client) {
        return new McpScannerSession(client, McpEventLog.noop(), () -> true,
                Duration.ofSeconds(10));
    }

    private static McpServerConfig config() {
        return new McpServerConfig(ENDPOINT, TransportType.MODERN_HTTP, new NoAuthStrategy());
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<InputStream> response(String body) {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("application/json")),
                (name, value) -> true));
        when(response.body()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        return response;
    }
}
