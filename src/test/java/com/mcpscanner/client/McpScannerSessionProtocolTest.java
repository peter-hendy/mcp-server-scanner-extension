package com.mcpscanner.client;

import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.core.connection.ConnectionAuthException;
import com.mcpscanner.core.connection.CredentialProvider;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.core.protocol.McpRequests;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpScannerSessionProtocolTest {

    @Test
    void modernAdmissionPreservesMetadataAndSkipsLegacyHandshake() throws Exception {
        HttpClient client = mock(HttpClient.class);
        List<HttpRequest> requests = new ArrayList<>();
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    requests.add(request);
                    return response(200, "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"capabilities\":{},\"supportedVersions\":[\"2026-07-28\"],\"_meta\":{\"io.modelcontextprotocol/serverInfo\":{\"name\":\"modern\"}}}}", null);
                });

        McpScannerSession session = session(client);
        session.connect(config(TransportType.AUTO));

        assertThat(session.transportType()).isEqualTo(TransportType.MODERN_HTTP);
        assertThat(session.negotiatedProtocolVersion()).isEqualTo(McpRequests.MODERN_VERSION);
        assertThat(session.admissionResult()).containsKeys("supportedVersions", "_meta");
        assertThat(session.scannerHeaders()).doesNotContainKey("Mcp-Session-Id");
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).headers().firstValue("Mcp-Method")).contains("server/discover");
        assertThat(session.operation("tools/call", "{\"name\":\"demo\",\"arguments\":{}}", 9)
                .headers()).containsEntry("Mcp-Name", "demo");
    }

    @Test
    void httpSseAdmissionIsUnwrappedBeforeSharedFlowParsesIt() throws Exception {
        HttpClient client = mock(HttpClient.class);
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"capabilities\":{},\"supportedVersions\":[\"2026-07-28\"]}}";
        HttpResponse<InputStream> admission = response(200, "event: message\ndata: " + json + "\n\n",
                null, "text/event-stream");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(admission);

        McpScannerSession session = session(client);
        session.connect(config(TransportType.MODERN_HTTP));

        assertThat(session.transportType()).isEqualTo(TransportType.MODERN_HTTP);
        assertThat(session.admissionResult()).containsKey("supportedVersions");
    }

    @Test
    void legacyCounterofferUsesNegotiatedVersionAndSessionHeader() throws Exception {
        HttpClient client = mock(HttpClient.class);
        List<HttpRequest> requests = new ArrayList<>();
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    requests.add(request);
                    if (request.headers().firstValue("Mcp-Method").isPresent()) {
                        return response(404, "", null);
                    }
                    if (requests.size() == 2) {
                        return response(400, "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32602,\"data\":{\"supportedVersions\":[\"2025-03-26\"]}}}", null);
                    }
                    if (requests.size() == 3) {
                        return response(200, "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"serverInfo\":{\"name\":\"legacy\",\"version\":\"1\"}}}", "session-abc");
                    }
                    return response(202, "", null);
                });

        McpScannerSession session = session(client);
        session.connect(config(TransportType.AUTO));

        assertThat(session.transportType()).isEqualTo(TransportType.STREAMABLE_HTTP);
        assertThat(session.negotiatedProtocolVersion()).isEqualTo("2025-03-26");
        assertThat(session.scannerHeaders()).containsEntry("Mcp-Session-Id", "session-abc");
        assertThat(requests).hasSize(4);
        assertThat(session.nextRequestId()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2025-03-26", "2025-06-18", "2025-11-25"})
    void initializeHttpNegotiatesEveryUiAdvertisedProtocolVersion(String version) throws Exception {
        HttpClient client = mock(HttpClient.class);
        List<HttpRequest> requests = new ArrayList<>();
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    requests.add(request);
                    if (!version.equals(McpRequests.HANDSHAKE_VERSION) && requests.size() == 1) {
                        String supportedVersions = "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32602,\"data\":{\"supportedVersions\":[\""
                                + version + "\"]}}}";
                        return response(400, supportedVersions, null);
                    }
                    if (requests.size() == (version.equals(McpRequests.HANDSHAKE_VERSION) ? 1 : 2)) {
                        String initialized = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\""
                                + version + "\",\"capabilities\":{},\"serverInfo\":{\"name\":\"legacy\",\"version\":\"1\"}}}";
                        return response(200, initialized, "session-" + version);
                    }
                    return response(202, "", "session-" + version);
                });

        McpScannerSession session = session(client);
        session.connect(config(TransportType.STREAMABLE_HTTP));

        assertThat(session.transportType()).isEqualTo(TransportType.STREAMABLE_HTTP);
        assertThat(session.negotiatedProtocolVersion()).isEqualTo(version);
        assertThat(session.scannerHeaders())
                .containsEntry("MCP-Protocol-Version", version)
                .containsEntry("Mcp-Session-Id", "session-" + version);
        int notificationIndex = requests.size() - 1;
        assertThat(requests).hasSize(version.equals(McpRequests.HANDSHAKE_VERSION) ? 2 : 3);
        assertThat(requests.get(notificationIndex).headers().firstValue("MCP-Protocol-Version")).contains(version);
        assertThat(requests.get(notificationIndex).headers().firstValue("Mcp-Session-Id"))
                .contains("session-" + version);

        var operation = session.operation("tools/call", "{\"name\":\"demo\",\"arguments\":{}}", 9);
        assertThat(operation.headers())
                .containsEntry("MCP-Protocol-Version", version)
                .containsEntry("MCP-Session-Id", "session-" + version);
    }

    @Test
    void sseHandshakeUsesEndpointEventAndInitializeReply() throws Exception {
        HttpClient client = mock(HttpClient.class);
        QueuedInputStream events = new QueuedInputStream();
        events.append("event: endpoint\ndata: /messages\n\n");
        HttpResponse<InputStream> stream = response(200, "", null);
        when(stream.body()).thenReturn(events);
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(stream));
        AtomicInteger posts = new AtomicInteger();
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    if (posts.incrementAndGet() == 1) {
                        events.append("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"serverInfo\":{\"name\":\"sse\",\"version\":\"1\"}}}\n\n");
                    }
                    return response(202, "", null);
                });

        McpScannerSession session = session(client);
        session.connect(new McpServerConfig("http://localhost:8080/sse", TransportType.SSE,
                new NoAuthStrategy()));

        assertThat(session.transportType()).isEqualTo(TransportType.SSE);
        assertThat(session.resolvedEndpoint()).isEqualTo("http://localhost:8080/messages");
        assertThat(session.negotiatedProtocolVersion()).isEqualTo(McpRequests.SSE_VERSION);
        assertThat(session.admissionResult()).containsKey("serverInfo");
        assertThat(posts.get()).isEqualTo(2);
        session.disconnect();
    }

    @Test
    void sseScannerHeadersUseFreshSharedCredentialsAndStopOnProviderRefusal() throws Exception {
        HttpClient client = mock(HttpClient.class);
        QueuedInputStream events = new QueuedInputStream();
        events.append("event: endpoint\ndata: /messages\n\n");
        HttpResponse<InputStream> stream = response(200, "", null);
        when(stream.body()).thenReturn(events);
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(stream));
        AtomicInteger posts = new AtomicInteger();
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    if (posts.incrementAndGet() == 1) {
                        events.append("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"serverInfo\":{\"name\":\"sse\",\"version\":\"1\"}}}\n\n");
                    }
                    return response(202, "", null);
                });
        AtomicReference<String> token = new AtomicReference<>("fresh");
        AtomicBoolean refused = new AtomicBoolean();
        CredentialProvider provider = () -> {
            if (refused.get()) throw new IllegalStateException("Credentials unavailable");
            return Map.of("Authorization", "Bearer " + token.get());
        };
        AuthStrategy auth = new AuthStrategy() {
            @Override public Map<String, String> headers() { return Map.of("Authorization", "Bearer cached"); }
            @Override public java.util.Optional<CredentialProvider> sharedCredentialProvider() {
                return java.util.Optional.of(provider);
            }
        };
        McpScannerSession session = session(client);
        session.connect(new McpServerConfig("http://localhost:8080/sse", TransportType.SSE, auth));

        assertThat(session.scannerHeaders()).containsEntry("Authorization", "Bearer fresh");
        token.set("rotated");
        assertThat(session.scannerHeaders()).containsEntry("Authorization", "Bearer rotated");
        refused.set(true);
        assertThatThrownBy(session::scannerHeaders).isInstanceOf(ConnectionAuthException.class);
        session.disconnect();
        assertThat(session.scannerHeaders()).isEmpty();
    }

    @Test
    void sseNetworkUsesSharedCredentialsAndStopsAfterProviderRefusal() throws Exception {
        HttpClient client = mock(HttpClient.class);
        QueuedInputStream events = new QueuedInputStream();
        events.append("event: endpoint\ndata: /messages\n\n");
        HttpResponse<InputStream> stream = response(200, "", null);
        when(stream.body()).thenReturn(events);
        AtomicReference<String> getAuthorization = new AtomicReference<>();
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    getAuthorization.set(request.headers().firstValue("Authorization").orElse(""));
                    return CompletableFuture.completedFuture(stream);
                });
        List<String> postAuthorizations = new ArrayList<>();
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    postAuthorizations.add(request.headers().firstValue("Authorization").orElse(""));
                    if (postAuthorizations.size() == 1) {
                        events.append("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"serverInfo\":{\"name\":\"sse\",\"version\":\"1\"}}}\n\n");
                    } else if (postAuthorizations.size() == 3) {
                        events.append("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"tools\":[]}}\n\n");
                    }
                    return response(202, "", null);
                });
        AtomicBoolean refused = new AtomicBoolean();
        CredentialProvider provider = () -> {
            if (refused.get()) throw new IllegalStateException("Credentials unavailable");
            return Map.of("Authorization", "Bearer current");
        };
        AuthStrategy auth = mock(AuthStrategy.class);
        when(auth.headers()).thenReturn(Map.of("Authorization", "Bearer cached"));
        when(auth.sharedCredentialProvider()).thenReturn(java.util.Optional.of(provider));
        McpScannerSession session = session(client);
        session.connect(new McpServerConfig("http://localhost:8080/sse", TransportType.SSE, auth));
        try {
            assertThat(getAuthorization).hasValue("Bearer current");
            assertThat(postAuthorizations).containsExactly("Bearer current", "Bearer current");
            refused.set(true);
            assertThatThrownBy(() -> session.catalog("tools/list", null))
                    .isInstanceOf(ConnectionAuthException.class);
            assertThat(postAuthorizations).hasSize(2);
        } finally {
            session.disconnect();
        }
    }

    @Test
    void sseFallbackKeepsStreamForInitializeReply() throws Exception {
        HttpClient client = mock(HttpClient.class);
        QueuedInputStream events = new QueuedInputStream();
        events.append("event: ping\ndata: alive\n\n".repeat(32));
        HttpResponse<InputStream> stream = response(200, "", null);
        when(stream.body()).thenReturn(events);
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(stream));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    assertThat(request.uri().toString()).isEqualTo("http://localhost:8080/message");
                    if (request.bodyPublisher().isPresent()) {
                        events.append("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"serverInfo\":{\"name\":\"fallback\",\"version\":\"1\"}}}\n\n");
                    }
                    return response(202, "", null);
                });

        McpScannerSession session = session(client);
        session.connect(new McpServerConfig("http://localhost:8080/sse", TransportType.SSE,
                new NoAuthStrategy()));

        assertThat(session.resolvedEndpoint()).isEqualTo("http://localhost:8080/message");
        assertThat(session.transportType()).isEqualTo(TransportType.SSE);
        session.disconnect();
    }

    @Test
    void closedSseStreamFailsExplicitAndAutoConnections() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<InputStream> closedStream = response(200, "event: ping\ndata: alive\n\n", null);
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(closedStream));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> response(404, "", null));

        for (TransportType requested : List.of(TransportType.SSE, TransportType.AUTO)) {
            McpScannerSession session = session(client);
            assertThatThrownBy(() -> session.connect(new McpServerConfig(
                    "http://localhost:8080/sse", requested, new NoAuthStrategy())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to establish scanner session");
            assertThat(session.transportType()).isNull();
            assertThat(session.resolvedEndpoint()).isNull();
        }
    }

    private static McpScannerSession session(HttpClient client) {
        return new McpScannerSession(client, new McpEventLog(null));
    }

    private static McpServerConfig config(TransportType transport) {
        return new McpServerConfig("http://localhost:8080/mcp", transport, new NoAuthStrategy());
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<InputStream> response(int status, String body, String sessionId) {
        return response(status, body, sessionId, "application/json");
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<InputStream> response(int status, String body, String sessionId,
                                                       String contentType) {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        Map<String, List<String>> entries = sessionId == null ? Map.of("Content-Type", List.of(contentType))
                : Map.of("Content-Type", List.of(contentType), "Mcp-Session-Id", List.of(sessionId));
        HttpHeaders headers = HttpHeaders.of(entries, (name, value) -> true);
        when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(headers);
        when(response.body()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        return response;
    }

    private static final class QueuedInputStream extends InputStream {
        private static final int END_OF_STREAM = -1;
        private final BlockingQueue<Integer> bytes = new LinkedBlockingQueue<>();

        void append(String value) {
            for (byte next : value.getBytes(StandardCharsets.UTF_8)) {
                bytes.add(next & 0xff);
            }
        }

        @Override
        public int read() throws IOException {
            try {
                return bytes.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("SSE read interrupted", e);
            }
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) return 0;
            int first = read();
            if (first == END_OF_STREAM) return -1;
            buffer[offset] = (byte) first;
            int count = 1;
            while (count < length) {
                Integer next = bytes.poll();
                if (next == null) break;
                if (next == END_OF_STREAM) {
                    bytes.add(END_OF_STREAM);
                    break;
                }
                buffer[offset + count++] = (byte) (int) next;
            }
            return count;
        }

        @Override
        public void close() {
            bytes.add(END_OF_STREAM);
        }
    }
}
