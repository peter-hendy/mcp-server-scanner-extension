package com.mcpscanner.client;

import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.core.protocol.McpRequests;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.mcpscanner.testutil.AwaitedFutures.doneWithin;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpScannerSessionBodyDeadlineTest {

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "text/event-stream"})
    void stalledBodyFailsAndReleasesSessionMonitor(String contentType) throws Exception {
        StalledBody stalled = new StalledBody(true);
        McpScannerSession session = connectedSession(List.of(stalled), contentType);
        CompletableFuture<?> operation = CompletableFuture.runAsync(() -> session.sendHttpOperation(
                session.operation("tools/list", "{}", 2)));
        try {
            await().atMost(2, SECONDS).pollInterval(10, MILLISECONDS).untilTrue(stalled.started);
            assertThatThrownBy(doneWithin(operation, Duration.ofSeconds(2))::join)
                    .isInstanceOf(CompletionException.class)
                    .hasRootCauseMessage("MCP response body read timed out");
            assertThat(stalled.closed.isDone()).isTrue();
            doneWithin(CompletableFuture.runAsync(session::disconnect), Duration.ofSeconds(2)).join();
            assertThat(session.resolvedEndpoint()).isNull();
        } finally {
            stalled.release.complete(null);
            doneWithin(operation, Duration.ofSeconds(2));
        }
    }

    @Test
    void eachStalledBodyTimesOutOnItsOwnReader() throws Exception {
        List<StalledBody> bodies = new ArrayList<>();
        for (int i = 0; i < 5; i++) bodies.add(new StalledBody(false));
        McpScannerSession session = connectedSession(bodies, "application/json");
        try {
            for (int i = 0; i < bodies.size(); i++) {
                McpRequests.Request request = session.operation("tools/list", "{}", i + 2);
                CompletableFuture<?> operation = CompletableFuture.runAsync(
                        () -> session.sendHttpOperation(request));
                assertThatThrownBy(doneWithin(operation, Duration.ofSeconds(2))::join)
                        .isInstanceOf(CompletionException.class)
                        .hasRootCauseMessage("MCP response body read timed out");
                assertThat(bodies.get(i).closed.isDone()).isTrue();
            }
            assertThat(bodies).allSatisfy(body -> assertThat(body.reads).hasValue(1));
            doneWithin(CompletableFuture.runAsync(session::disconnect), Duration.ofSeconds(2)).join();
        } finally {
            bodies.forEach(body -> body.release.complete(null));
        }
    }

    private McpScannerSession connectedSession(List<StalledBody> bodies, String contentType) throws Exception {
        HttpClient client = mock(HttpClient.class);
        AtomicInteger sends = new AtomicInteger();
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    int index = sends.getAndIncrement();
                    if (index == 0) {
                        String admission = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"capabilities\":{},\"supportedVersions\":[\"2026-07-28\"]}}";
                        return response(new ByteArrayInputStream(admission.getBytes(StandardCharsets.UTF_8)),
                                "application/json");
                    }
                    return response(bodies.get(index - 1), contentType);
                });
        McpScannerSession session = new McpScannerSession(client, McpEventLog.noop(), () -> true, Duration.ofMillis(150));
        session.connect(new McpServerConfig("http://127.0.0.1/mcp", TransportType.MODERN_HTTP,
                new NoAuthStrategy()));
        return session;
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<InputStream> response(InputStream body, String contentType) {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of(contentType)),
                (name, value) -> true));
        when(response.body()).thenReturn(body);
        return response;
    }

    private static final class StalledBody extends InputStream {
        private final AtomicBoolean started = new AtomicBoolean();
        private final CompletableFuture<Void> closed = new CompletableFuture<>();
        private final CompletableFuture<Void> release = new CompletableFuture<>();
        private final AtomicInteger reads = new AtomicInteger();
        private final boolean releaseOnClose;

        private StalledBody(boolean releaseOnClose) {
            this.releaseOnClose = releaseOnClose;
        }

        @Override
        public int read() {
            reads.incrementAndGet();
            started.set(true);
            release.join();
            return -1;
        }

        @Override
        public void close() {
            closed.complete(null);
            if (releaseOnClose) release.complete(null);
        }
    }
}
