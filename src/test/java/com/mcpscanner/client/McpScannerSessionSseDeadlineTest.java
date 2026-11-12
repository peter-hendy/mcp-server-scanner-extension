package com.mcpscanner.client;

import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.logging.McpEventLog;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.mcpscanner.testutil.AwaitedFutures.doneWithin;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpScannerSessionSseDeadlineTest {
    private static final String ENDPOINT = "event: endpoint\ndata: /messages\n\n";
    private static final String INITIALIZED = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"serverInfo\":{\"name\":\"fixture\",\"version\":\"1\"}}}\n\n";

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void silentOpenStreamTimesOutAndReleasesSessionMonitor(boolean catalog) throws Exception {
        SilentStream body = new SilentStream(ENDPOINT + (catalog ? INITIALIZED : ""));
        HttpClient client = mock(HttpClient.class);
        var streamResponse = response(200, body);
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(streamResponse));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> response(202, new ByteArrayInputStream(new byte[0])));
        McpScannerSession session = new McpScannerSession(client, McpEventLog.noop(), () -> true, Duration.ofMillis(150));
        McpServerConfig config = new McpServerConfig("http://localhost/sse", TransportType.SSE, new NoAuthStrategy());
        if (catalog) session.connect(config);
        CompletableFuture<?> operation = CompletableFuture.runAsync(() -> {
            if (catalog) session.catalog("tools/list", null);
            else session.connect(config);
        });
        try {
            await().atMost(2, SECONDS).pollInterval(10, MILLISECONDS).untilTrue(body.started);
            assertThatThrownBy(doneWithin(operation, Duration.ofSeconds(2))::join)
                    .isInstanceOf(CompletionException.class)
                    .hasStackTraceContaining("SSE reply wait timed out");
            assertThat(body.closed.isDone()).isTrue();
            doneWithin(CompletableFuture.runAsync(session::disconnect), Duration.ofSeconds(2)).join();
            assertThat(session.resolvedEndpoint()).isNull();
        } finally {
            body.release.complete(null);
            doneWithin(operation, Duration.ofSeconds(2));
            session.disconnect();
        }
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<InputStream> response(int status, InputStream body) {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (name, value) -> true));
        when(response.body()).thenReturn(body);
        return response;
    }

    private static final class SilentStream extends InputStream {
        private final ByteArrayInputStream prefix;
        private final AtomicBoolean started = new AtomicBoolean();
        private final CompletableFuture<Void> closed = new CompletableFuture<>();
        private final CompletableFuture<Void> release = new CompletableFuture<>();

        private SilentStream(String prefix) {
            this.prefix = new ByteArrayInputStream(prefix.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public int read() {
            int value = prefix.read();
            if (value != -1) return value;
            started.set(true);
            release.join();
            return -1;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (prefix.available() > 0) return prefix.read(buffer, offset, length);
            return read();
        }

        @Override
        public void close() {
            closed.complete(null);
            release.complete(null);
        }
    }
}
