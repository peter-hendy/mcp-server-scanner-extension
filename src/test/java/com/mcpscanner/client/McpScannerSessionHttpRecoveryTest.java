package com.mcpscanner.client;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.core.connection.CredentialProvider;
import com.mcpscanner.core.connection.OperationRecovery;
import com.mcpscanner.core.connection.RecoveryResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.mcpscanner.testutil.AwaitedFutures.doneWithin;
import static org.assertj.core.api.Assertions.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpScannerSessionHttpRecoveryTest {
    @Test
    void reconnectCompletesWhileOldCatalogScopeApprovalIsPending() throws Exception {
        HttpClient client = mock(HttpClient.class);
        List<HttpRequest> requests = new CopyOnWriteArrayList<>();
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    requests.add(request);
                    if (request.headers().firstValue("Mcp-Method").orElse("").equals("server/discover")) {
                        return response(200, "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"capabilities\":{},\"supportedVersions\":[\"2026-07-28\"]}}");
                    }
                    return response(403, "");
                });
        AtomicBoolean approvalEntered = new AtomicBoolean();
        CompletableFuture<Void> approve = new CompletableFuture<>();
        AtomicReference<String> tokenA = new AtomicReference<>("Bearer A");
        CredentialProvider providerA = new CredentialProvider() {
            @Override public Map<String, String> headers() { return Map.of("Authorization", tokenA.get()); }
            @Override public OperationRecovery beginOperation(String operation) {
                return response -> {
                    approvalEntered.set(true);
                    approve.join();
                    tokenA.set("Bearer A-expanded");
                    return RecoveryResult.RETRY;
                };
            }
        };
        McpScannerSession session = new McpScannerSession(client, new McpEventLog(null));
        session.connect(config("a", providerA));
        var workers = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Throwable> oldOperation = CompletableFuture.supplyAsync(() -> {
                try {
                    session.catalog("tools/list", null);
                    return null;
                } catch (Throwable failure) {
                    return failure;
                }
            }, workers);
            await().atMost(3, SECONDS).pollInterval(10, MILLISECONDS).untilTrue(approvalEntered);
            CompletableFuture<Void> reconnect = CompletableFuture.runAsync(
                    () -> session.connect(config("b", () -> Map.of("Authorization", "Bearer B"))), workers);
            doneWithin(reconnect, Duration.ofSeconds(3)).join();
            long generationB = session.connectionGeneration();
            approve.complete(null);

            assertThat(doneWithin(oldOperation, Duration.ofSeconds(3)).join()).isInstanceOf(IllegalStateException.class);
            assertThat(session.connectionGeneration()).isEqualTo(generationB);
            assertThat(session.resolvedEndpoint()).isEqualTo("http://localhost:8080/b");
            assertThat(session.scannerHeaders()).containsEntry("Authorization", "Bearer B");
            assertThat(requests).hasSize(3);
            assertThat(requests.get(1).uri().getPath()).isEqualTo("/a");
            assertThat(requests.get(1).headers().firstValue("Authorization")).contains("Bearer A");
            assertThat(requests.get(2).uri().getPath()).isEqualTo("/b");
            assertThat(requests.get(2).headers().firstValue("Authorization")).contains("Bearer B");
        } finally {
            approve.complete(null);
            workers.shutdownNow();
        }
    }

    @Test
    void currentConnectionRecoversOutsideMonitorAndRetainsNegotiatedSession() throws Exception {
        HttpClient client = mock(HttpClient.class);
        List<HttpRequest> requests = new CopyOnWriteArrayList<>();
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    requests.add(request);
                    if (requests.size() == 1) {
                        HttpResponse<InputStream> admission = response(200,
                                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"serverInfo\":{\"name\":\"legacy\",\"version\":\"1\"}}}");
                        when(admission.headers()).thenReturn(HttpHeaders.of(
                                Map.of("Mcp-Session-Id", List.of("session-a")), (name, value) -> true));
                        return admission;
                    }
                    if (requests.size() == 2) return response(202, "");
                    if (requests.size() == 3) return response(403, "");
                    return response(200, "{\"jsonrpc\":\"2.0\",\"id\":9,\"result\":{}}");
                });
        McpScannerSession session = new McpScannerSession(client, new McpEventLog(null));
        AtomicReference<String> token = new AtomicReference<>("Bearer initial");
        CredentialProvider provider = new CredentialProvider() {
            @Override public Map<String, String> headers() { return Map.of("Authorization", token.get()); }
            @Override public OperationRecovery beginOperation(String operation) {
                return response -> {
                    assertThat(Thread.holdsLock(session)).isFalse();
                    token.set("Bearer expanded");
                    return RecoveryResult.RETRY;
                };
            }
        };
        session.connect(new McpServerConfig("http://localhost:8080/a", TransportType.STREAMABLE_HTTP,
                config("a", provider).auth()));
        long generation = session.connectionGeneration();

        var reply = session.sendHttpOperation(session.operation("tools/call", "{\"name\":\"demo\",\"arguments\":{}}", 9));

        assertThat(reply.status()).isEqualTo(200);
        assertThat(session.connectionGeneration()).isEqualTo(generation);
        assertThat(session.negotiatedProtocolVersion()).isEqualTo("2025-03-26");
        assertThat(session.scannerHeaders()).containsEntry("Mcp-Session-Id", "session-a")
                .containsEntry("Authorization", "Bearer expanded");
        assertThat(requests).hasSize(4);
        for (HttpRequest request : requests.subList(2, 4)) {
            assertThat(request.uri().getPath()).isEqualTo("/a");
            assertThat(request.headers().firstValue("Mcp-Session-Id")).contains("session-a");
            assertThat(request.headers().firstValue("MCP-Protocol-Version")).contains("2025-03-26");
        }
        assertThat(requests.get(2).headers().firstValue("Authorization")).contains("Bearer initial");
        assertThat(requests.get(3).headers().firstValue("Authorization")).contains("Bearer expanded");
    }

    @Test
    void olderOperationCannotPublishSessionSnapshotAfterNewerRecovery() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> response(200,
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"serverInfo\":{\"name\":\"legacy\",\"version\":\"1\"}}}"));
        McpScannerSession session = new McpScannerSession(client, new McpEventLog(null));
        session.connect(new McpServerConfig("http://localhost:8080/a", TransportType.STREAMABLE_HTTP,
                new com.mcpscanner.auth.NoAuthStrategy()));
        var shared = mock(com.mcpscanner.core.connection.HttpSession.class);
        var firstSnapshot = new AtomicBoolean();
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        when(shared.send(any())).thenReturn(new com.mcpscanner.core.protocol.McpFlow.HttpResponse(
                200, Map.of(), "{}", true));
        when(shared.session()).thenAnswer(invocation -> {
            int read = reads.incrementAndGet();
            if (read == 1) firstSnapshot.set(true);
            return new com.mcpscanner.core.protocol.McpFlow.Session(
                    "2025-03-26", "session-" + read, "/a", "fixture", "1");
        });
        var field = McpScannerSession.class.getDeclaredField("httpSession");
        field.setAccessible(true);
        field.set(session, shared);
        var older = session.prepareHttpOperation(session.operation("tools/list", "{}", 9));
        var newer = session.prepareHttpOperation(session.operation("tools/list", "{}", 10));
        var worker = Executors.newSingleThreadExecutor();
        CompletableFuture<?> olderResult;
        try {
            synchronized (session) {
                olderResult = CompletableFuture.supplyAsync(older, worker);
                await().atMost(3, SECONDS).pollInterval(10, MILLISECONDS).untilTrue(firstSnapshot);
                newer.get();
                assertThat(session.protocolHeaders()).containsEntry("Mcp-Session-Id", "session-2");
            }
            doneWithin(olderResult, Duration.ofSeconds(3)).join();
            assertThat(session.protocolHeaders()).containsEntry("Mcp-Session-Id", "session-2");
            assertThat(session.scannerHeaders()).containsEntry("Mcp-Session-Id", "session-2");
        } finally {
            worker.shutdownNow();
        }
    }

    private static McpServerConfig config(String path, CredentialProvider provider) {
        AuthStrategy auth = new AuthStrategy() {
            @Override public Map<String, String> headers() { return provider.headers(); }
            @Override public Optional<CredentialProvider> sharedCredentialProvider() { return Optional.of(provider); }
        };
        return new McpServerConfig("http://localhost:8080/" + path, TransportType.MODERN_HTTP, auth);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<InputStream> response(int status, String body) {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("application/json")),
                (name, value) -> true));
        when(response.body()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        return response;
    }
}
