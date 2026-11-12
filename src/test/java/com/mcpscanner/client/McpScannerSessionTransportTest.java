package com.mcpscanner.client;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.config.ClientIdentityProvider;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.BoundedBodyReader;
import com.mcpscanner.core.connection.CredentialProvider;
import com.mcpscanner.core.connection.OperationRecovery;
import com.mcpscanner.core.connection.RecoveryResult;
import com.mcpscanner.testutil.FakeMcpServer;
import com.mcpscanner.testutil.ScriptedProtocolTransport;
import com.mcpscanner.testutil.ScriptedProtocolTransport.ScriptedIoFailure;
import com.mcpscanner.core.connection.SseOpenException;
import com.mcpscanner.core.protocol.McpFlow;
import com.mcpscanner.core.protocol.McpResponses;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.ssl.SSLHandshakeException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class McpScannerSessionTransportTest {

    private static final String ENDPOINT = "http://localhost:8080/mcp";
    private static final String HTTPS_ENDPOINT = "https://localhost:8443/mcp";

    private final HttpClient httpClient = mock(HttpClient.class);
    private final McpEventLog eventLog = new McpEventLog(null);
    private final ScriptedProtocolTransport transport = ScriptedProtocolTransport.fakeServer();

    @Test
    void modernConnectAndCatalogGoThroughInjectedTransport() {
        McpScannerSession session = session(transport);

        session.connect(config(ENDPOINT, TransportType.MODERN_HTTP));
        session.catalog("tools/list", null);

        verifyNoInteractions(httpClient);
        assertThat(transport.methods()).containsExactly("server/discover", "tools/list");
    }

    @Test
    void streamableHandshakeGoesThroughInjectedTransportAndCapturesSessionId() {
        McpScannerSession session = session(transport);

        session.connect(config(ENDPOINT, TransportType.STREAMABLE_HTTP));

        verifyNoInteractions(httpClient);
        assertThat(transport.methods()).containsExactly("initialize", "notifications/initialized");
        assertThat(session.scannerHeaders()).containsEntry("Mcp-Session-Id", FakeMcpServer.SESSION_ID);
        assertThat(session.negotiatedProtocolVersion()).isEqualTo(FakeMcpServer.LEGACY_VERSION);
    }

    @Test
    void lowercaseSessionIdReplyHeaderIsExposedAsCanonicalScannerHeader() {
        McpScannerSession session = session(new ScriptedProtocolTransport(request ->
                "initialize".equals(FakeMcpServer.method(request))
                        ? FakeMcpServer.exchange(request, 200, Map.of("content-type", List.of("application/json"),
                                "mcp-session-id", List.of(FakeMcpServer.SESSION_ID)),
                                FakeMcpServer.initializeReply(request))
                        : FakeMcpServer.respond(request)));

        session.connect(config(ENDPOINT, TransportType.STREAMABLE_HTTP));

        assertThat(session.scannerHeaders()).containsEntry("Mcp-Session-Id", FakeMcpServer.SESSION_ID);
    }

    @Test
    void disconnectSendsSessionDeleteThroughInjectedTransport() {
        McpScannerSession session = session(transport);
        session.connect(config(ENDPOINT, TransportType.STREAMABLE_HTTP));

        session.disconnect();

        verifyNoInteractions(httpClient);
        ProtocolHttpRequest delete = transport.requests().get(transport.requests().size() - 1);
        assertThat(delete.method()).isEqualTo("DELETE");
        assertThat(delete.uri()).isEqualTo(URI.create(ENDPOINT));
        assertThat(delete.headers()).containsEntry("Mcp-Session-Id", FakeMcpServer.SESSION_ID);
    }

    @Test
    void bufferedEventStreamBodyIsParsedForTheJsonRpcReply() {
        ScriptedProtocolTransport eventStreams = new ScriptedProtocolTransport(request -> {
            ProtocolHttpResponse reply = FakeMcpServer.respond(request);
            if (!"initialize".equals(FakeMcpServer.method(request))) return reply;
            String json = FakeMcpServer.initializeReply(request);
            return FakeMcpServer.exchange(request, 200, Map.of("Content-Type", List.of("text/event-stream"),
                    "Mcp-Session-Id", List.of(FakeMcpServer.SESSION_ID)), "event: message\ndata: " + json + "\n\n");
        });
        McpScannerSession session = session(eventStreams);

        session.connect(config(ENDPOINT, TransportType.STREAMABLE_HTTP));

        assertThat(session.negotiatedProtocolVersion()).isEqualTo(FakeMcpServer.LEGACY_VERSION);
        assertThat(session.admissionResult()).containsKey("serverInfo");
    }

    @Test
    void serverPingInEventStreamReplyIsAnsweredThroughInjectedTransport() {
        String ping = "{\"jsonrpc\":\"2.0\",\"id\":\"server-ping-1\",\"method\":\"ping\"}";
        ScriptedProtocolTransport pinging = new ScriptedProtocolTransport(request -> {
            if (!"tools/list".equals(FakeMcpServer.method(request))) return FakeMcpServer.respond(request);
            String reply = readBody(FakeMcpServer.respond(request));
            return FakeMcpServer.exchange(request, 200, Map.of("Content-Type", List.of("text/event-stream")),
                    "event: message\ndata: " + ping + "\n\nevent: message\ndata: " + reply + "\n\n");
        });
        McpScannerSession session = session(pinging);
        session.connect(config(ENDPOINT, TransportType.STREAMABLE_HTTP));

        McpResponses.Reply catalog = session.catalog("tools/list", null);

        verifyNoInteractions(httpClient);
        assertThat(catalog.result()).containsKey("tools");
        assertThat(pinging.requests()).filteredOn(request -> request.hasBody()
                        && request.body().contains("\"server-ping-1\"") && request.body().contains("\"result\""))
                .singleElement()
                .satisfies(answer -> assertThat(answer.headers().entrySet()).anySatisfy(header -> {
                    assertThat(header.getKey()).isEqualToIgnoringCase("Mcp-Session-Id");
                    assertThat(header.getValue()).isEqualTo(FakeMcpServer.SESSION_ID);
                }));
    }

    @Test
    void bufferedBodyOverTheResponseBoundIsRejected() {
        String oversized = "a".repeat(BoundedBodyReader.MAX_RESPONSE_BYTES + 1);
        McpScannerSession session = session(new ScriptedProtocolTransport(request ->
                FakeMcpServer.exchange(request, 200, Map.of("Content-Type", List.of("application/json")), oversized)));

        assertThatThrownBy(() -> session.connect(config(ENDPOINT, TransportType.MODERN_HTTP)))
                .isInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("exceeded " + BoundedBodyReader.MAX_RESPONSE_BYTES);
    }

    @Test
    void unauthorizedCatalogReplyClosesBodyAndRetriesWithRefreshedCredentials() {
        TrackingBody refusedBody = new TrackingBody("hostile-secret");
        AtomicBoolean refused = new AtomicBoolean();
        ScriptedProtocolTransport refusing = new ScriptedProtocolTransport(request -> {
            if ("tools/list".equals(FakeMcpServer.method(request)) && !refused.getAndSet(true)) {
                return new ProtocolHttpResponse(401, Map.of("WWW-Authenticate",
                        List.of("Bearer error=\"invalid_token\"")), refusedBody, request.uri());
            }
            return FakeMcpServer.respond(request);
        });
        AtomicReference<String> token = new AtomicReference<>("Bearer old");
        McpScannerSession session = session(refusing);

        session.connect(new McpServerConfig(ENDPOINT, TransportType.MODERN_HTTP, rotating(token)));
        session.catalog("tools/list", null);

        List<ProtocolHttpRequest> catalogCalls = refusing.requests().stream()
                .filter(request -> "tools/list".equals(FakeMcpServer.method(request))).toList();
        assertThat(catalogCalls).extracting(request -> request.headers().get("Authorization"))
                .containsExactly("Bearer old", "Bearer new");
        assertThat(refusedBody.closed).isTrue();
    }

    @Test
    void forbiddenCatalogReplyClosesBodyAndFailsTheCatalogWithTheStatus() {
        TrackingBody forbiddenBody = new TrackingBody("hostile-secret");
        ScriptedProtocolTransport forbidding = new ScriptedProtocolTransport(request ->
                "tools/list".equals(FakeMcpServer.method(request))
                        ? new ProtocolHttpResponse(403, Map.of(), forbiddenBody, request.uri())
                        : FakeMcpServer.respond(request));
        McpScannerSession session = session(forbidding);
        session.connect(config(ENDPOINT, TransportType.MODERN_HTTP));

        assertThatThrownBy(() -> session.catalog("tools/list", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Catalog request failed: HTTP 403");
        assertThat(forbiddenBody.closed).isTrue();
    }

    @Test
    void sameHostRedirectIsFollowedThroughInjectedTransportAndEndpointAdopted() {
        String moved = "http://localhost:8080/moved";
        ScriptedProtocolTransport redirecting = new ScriptedProtocolTransport(request -> request.uri().toString().equals(ENDPOINT)
                ? FakeMcpServer.exchange(request, 307, Map.of("Location", List.of(moved)), "")
                : FakeMcpServer.respond(request));
        McpScannerSession session = session(redirecting);

        session.connect(config(ENDPOINT, TransportType.MODERN_HTTP));
        session.catalog("tools/list", null);

        verifyNoInteractions(httpClient);
        assertThat(session.resolvedEndpoint()).isEqualTo(moved);
        assertThat(redirecting.requests()).extracting(request -> request.uri().toString())
                .containsExactly(ENDPOINT, moved, moved);
    }

    @Test
    void sseConnectNeverUsesInjectedTransport() throws IOException {
        try (McpFixtureServer server = new McpFixtureServer(McpFixtureProfile.sseOnly("2024-11-05"))) {
            McpScannerSession session = new McpScannerSession(HttpClient.newHttpClient(), transport,
                    eventLog, () -> true, ClientIdentityProvider.defaults());
            try {
                session.connect(config(server.endpoint(), TransportType.SSE));
                session.catalog("tools/list", null);

                assertThat(session.transportType()).isEqualTo(TransportType.SSE);
                assertThat(transport.requests()).isEmpty();
            } finally {
                session.disconnect();
            }
        }
    }

    @Test
    void sseTlsFailureLogsTheBurpSettingsHintOnce() {
        failSseStreamOpenWith(new SSLHandshakeException("PKIX path building failed"));
        McpScannerSession session = session(transport);

        assertThatThrownBy(() -> session.connect(config(HTTPS_ENDPOINT, TransportType.SSE)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(errorsLogged(McpScannerSession.SSE_TLS_HINT)).hasSize(1);
        assertThat(errorsLogged(McpScannerSession.SSE_FALLBACK_TLS_HINT)).isEmpty();
    }

    @Test
    void autoFallbackToSseWithTlsFailureLogsTheFallbackHintOnce() {
        failSseStreamOpenWith(new SSLHandshakeException("PKIX path building failed"));
        McpScannerSession session = session(new ScriptedProtocolTransport(request -> {
            throw new ScriptedIoFailure(new ConnectException("Connection refused"));
        }));

        assertThatThrownBy(() -> session.connect(config(HTTPS_ENDPOINT, TransportType.AUTO)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(errorsLogged(McpScannerSession.SSE_FALLBACK_TLS_HINT)).hasSize(1);
        assertThat(errorsLogged(McpScannerSession.SSE_TLS_HINT)).isEmpty();
    }

    @Test
    void sseTlsFailureWithBurpNetworkingOffTellsTheUserToTurnItOn() {
        failSseStreamOpenWith(new SSLHandshakeException("PKIX path building failed"));
        McpScannerSession session = session(transport);
        session.setUseBurpNetworking(() -> false);

        assertThatThrownBy(() -> session.connect(config(HTTPS_ENDPOINT, TransportType.SSE)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(errorsLogged(McpScannerSession.SSE_TLS_HINT_BURP_NETWORKING_OFF)).hasSize(1);
        assertThat(errorsLogged(McpScannerSession.SSE_TLS_HINT)).isEmpty();
        assertThat(McpScannerSession.SSE_TLS_HINT_BURP_NETWORKING_OFF)
                .contains("turn on \"" + BurpNetworkingSetting.NAME + "\"")
                .contains("Streamable HTTP or modern HTTP")
                .doesNotContain("do use Burp's settings");
    }

    @Test
    void autoFallbackToSseWithTlsFailureAndBurpNetworkingOffTellsTheUserToTurnItOn() {
        failSseStreamOpenWith(new SSLHandshakeException("PKIX path building failed"));
        McpScannerSession session = session(new ScriptedProtocolTransport(request -> {
            throw new ScriptedIoFailure(new ConnectException("Connection refused"));
        }));
        session.setUseBurpNetworking(() -> false);

        assertThatThrownBy(() -> session.connect(config(HTTPS_ENDPOINT, TransportType.AUTO)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(errorsLogged(McpScannerSession.SSE_FALLBACK_TLS_HINT_BURP_NETWORKING_OFF)).hasSize(1);
        assertThat(errorsLogged(McpScannerSession.SSE_FALLBACK_TLS_HINT)).isEmpty();
        assertThat(McpScannerSession.SSE_FALLBACK_TLS_HINT_BURP_NETWORKING_OFF)
                .contains("turn on \"" + BurpNetworkingSetting.NAME + "\"")
                .contains("Streamable HTTP or modern HTTP")
                .doesNotContain("uses Burp's settings");
    }

    @Test
    void httpTransportTlsFailureDoesNotLogTheSseHint() {
        McpScannerSession session = session(new ScriptedProtocolTransport(request -> {
            throw new ScriptedIoFailure(new SSLHandshakeException("PKIX path building failed"));
        }));

        assertThatThrownBy(() -> session.connect(config(HTTPS_ENDPOINT, TransportType.MODERN_HTTP)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(sseTlsHints()).isEmpty();
    }

    @Test
    void sseFailureCarryingTlsFailureOnlyAsSuppressedLogsTheHint() {
        RuntimeException failure = new RuntimeException("SSE open failed");
        failure.addSuppressed(new SSLHandshakeException("Received fatal alert: certificate_required"));
        failSseStreamOpenWith(failure);

        assertThatThrownBy(() -> session(transport).connect(config(HTTPS_ENDPOINT, TransportType.SSE)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(sseTlsHints()).hasSize(1);
    }

    @Test
    void httpsSseConnectionResetAfterTheClientHandshakeLogsTheHint() {
        failSseStreamOpenWith(new SocketException("Connection reset"));

        assertThatThrownBy(() -> session(transport).connect(config(HTTPS_ENDPOINT, TransportType.SSE)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(sseTlsHints()).hasSize(1);
    }

    @Test
    void plainHttpSseConnectionResetDoesNotLogTheHint() {
        failSseStreamOpenWith(new SocketException("Connection reset"));

        assertThatThrownBy(() -> session(transport).connect(config(ENDPOINT, TransportType.SSE)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(sseTlsHints()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("failuresBeforeOrWithoutTls")
    void httpsSseFailureThatIsNotTlsDoesNotLogTheHint(Exception failure) {
        failSseStreamOpenWith(failure);

        assertThatThrownBy(() -> session(transport).connect(config(HTTPS_ENDPOINT, TransportType.SSE)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(sseTlsHints()).isEmpty();
    }

    static Stream<Exception> failuresBeforeOrWithoutTls() {
        return Stream.of(
                new ConnectException("Connection refused"),
                new HttpConnectTimeoutException("HTTP connect timed out"),
                new HttpTimeoutException("request timed out"),
                (Exception) new IOException("unresolved").initCause(new UnresolvedAddressException()));
    }

    @Test
    void httpsSseRefusalWithAnHttpStatusDoesNotLogTheHint() {
        RuntimeException statusReceived = new SseOpenException(new McpFlow.HttpResponse(403, Map.of(), "", true));
        statusReceived.addSuppressed(new IOException("stream closed"));
        failSseStreamOpenWith(statusReceived);

        assertThatThrownBy(() -> session(transport).connect(config(HTTPS_ENDPOINT, TransportType.SSE)))
                .isInstanceOf(RuntimeException.class);

        assertThat(sseTlsHints()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 500})
    void httpsSseStreamThatAnsweredWithAStatusButNoEndpointDoesNotLogTheHint(int status) {
        answerSseStreamOpenWith(status, "");

        assertThatThrownBy(() -> session(transport).connect(config(HTTPS_ENDPOINT, TransportType.SSE)))
                .isInstanceOf(RuntimeException.class);

        assertThat(sseTlsHints()).isEmpty();
    }

    @Test
    void httpsSseEndpointEventPointingAtAnotherHostIsRejectedWithoutTheTlsHint() {
        answerSseStreamOpenWith(200, "event: endpoint\ndata: https://elsewhere.test/message\n\n");

        assertThatThrownBy(() -> session(transport).connect(config(HTTPS_ENDPOINT, TransportType.SSE)))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("cross-origin endpoint");

        assertThat(sseTlsHints()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private void answerSseStreamOpenWith(int status, String body) {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        when(response.uri()).thenReturn(URI.create(HTTPS_ENDPOINT));
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(response));
    }

    @SuppressWarnings("unchecked")
    private void failSseStreamOpenWith(Exception failure) {
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.failedFuture(failure));
    }

    private List<McpEventLog.LogEntry> sseTlsHints() {
        return eventLog.snapshot().stream()
                .filter(entry -> entry.level() == McpEventLog.Level.ERROR)
                .filter(entry -> List.of(McpScannerSession.SSE_TLS_HINT, McpScannerSession.SSE_FALLBACK_TLS_HINT,
                        McpScannerSession.SSE_TLS_HINT_BURP_NETWORKING_OFF,
                        McpScannerSession.SSE_FALLBACK_TLS_HINT_BURP_NETWORKING_OFF).contains(entry.message()))
                .toList();
    }

    private List<McpEventLog.LogEntry> errorsLogged(String message) {
        return eventLog.snapshot().stream()
                .filter(entry -> entry.level() == McpEventLog.Level.ERROR)
                .filter(entry -> entry.message().equals(message))
                .toList();
    }

    private static String readBody(ProtocolHttpResponse response) {
        try (InputStream body = response.body()) {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private McpScannerSession session(ProtocolHttpTransport injected) {
        return new McpScannerSession(httpClient, injected, eventLog, () -> true,
                ClientIdentityProvider.defaults());
    }

    private static McpServerConfig config(String endpoint, TransportType transport) {
        return new McpServerConfig(endpoint, transport, new NoAuthStrategy());
    }

    private static AuthStrategy rotating(AtomicReference<String> token) {
        CredentialProvider provider = new CredentialProvider() {
            @Override public Map<String, String> headers() { return Map.of("Authorization", token.get()); }
            @Override public OperationRecovery beginOperation(String operation) {
                return response -> {
                    token.set("Bearer new");
                    return RecoveryResult.RETRY;
                };
            }
        };
        return new AuthStrategy() {
            @Override public Map<String, String> headers() { return provider.headers(); }
            @Override public Optional<CredentialProvider> sharedCredentialProvider() { return Optional.of(provider); }
        };
    }

    private static final class TrackingBody extends ByteArrayInputStream {
        private volatile boolean closed;

        private TrackingBody(String body) {
            super(body.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
