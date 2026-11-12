package com.mcpscanner.client;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.config.RedirectMode;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.mcp.BoundedBodyReader;
import com.mcpscanner.core.connection.ConnectionAuthException;
import com.mcpscanner.core.connection.CredentialProvider;
import com.mcpscanner.core.connection.OAuthCredentialProvider;
import com.mcpscanner.core.connection.OAuthCredentialAccess;
import com.mcpscanner.core.oauth.AuthorizationApproval;
import com.mcpscanner.core.oauth.CredentialBinding;
import com.mcpscanner.core.oauth.OAuthCredential;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.Optional;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpScannerSessionTest {

    @Test
    void catalogRefreshesOnceWithoutReinitializingAValidSession() throws Exception {
        HttpClient client = mock(HttpClient.class);
        AtomicInteger initializations = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();
        List<HttpRequest> calls = new ArrayList<>();
        AuthStrategy auth = rotatingAuth(refreshes);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    String payload = body(request);
                    if (payload.contains("\"method\":\"initialize\"")) {
                        initializations.incrementAndGet();
                        return response(200, initializeReply("2025-06-18"), "session-valid");
                    }
                    if (payload.contains("tools/list")) {
                        calls.add(request);
                        if (calls.size() == 1) return response(401, "", null);
                        assertThat(request.headers().firstValue("Authorization")).contains("Bearer new");
                        assertThat(request.headers().firstValue("Mcp-Session-Id")).contains("session-valid");
                        return response(200, "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"tools\":[{\"name\":\"protected-tool\"}]}}", null);
                    }
                    return response(202, "", null);
                });
        McpScannerSession session = session(client);
        session.connect(new McpServerConfig("http://localhost/mcp", TransportType.STREAMABLE_HTTP, auth));

        assertThat(session.catalog("tools/list", null).result().toString()).contains("protected-tool");
        assertThat(refreshes).hasValue(1);
        assertThat(initializations).hasValue(1);
        assertThat(calls).hasSize(2);
        assertThat(session.scannerHeaders()).containsEntry("Authorization", "Bearer new");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2025-03-26", "2025-06-18", "2025-11-25"})
    void catalogRecoversExpiredSessionUsingTheNegotiatedVersion(String version) throws Exception {
        HttpClient client = mock(HttpClient.class);
        AtomicInteger initializations = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();
        List<HttpRequest> calls = new ArrayList<>();
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    String payload = body(request);
                    assertThat(request.headers().firstValue("Mcp-Method")).isEmpty();
                    if (payload.contains("\"method\":\"initialize\"")) {
                        int generation = initializations.incrementAndGet();
                        if (generation == 2) {
                            assertThat(payload).contains(version);
                            assertThat(request.headers().firstValue("Authorization")).contains("Bearer new");
                        }
                        return response(200, initializeReply(version), "session-" + generation);
                    }
                    if (payload.contains("tools/list")) {
                        calls.add(request);
                        if (calls.size() == 1) return response(401, "", null);
                        if (calls.size() == 2) return response(404, "", null);
                        assertThat(request.headers().firstValue("Authorization")).contains("Bearer new");
                        assertThat(request.headers().firstValue("Mcp-Session-Id")).contains("session-2");
                        assertThat(request.headers().firstValue("MCP-Protocol-Version")).contains(version);
                        return response(200, "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"tools\":[{\"name\":\"protected-tool\"}]}}", null);
                    }
                    return response(202, "", null);
                });
        McpScannerSession session = session(client);
        session.connect(new McpServerConfig("http://localhost/mcp", TransportType.STREAMABLE_HTTP,
                rotatingAuth(refreshes)));

        assertThat(session.catalog("tools/list", null).result().toString()).contains("protected-tool");
        assertThat(session.negotiatedProtocolVersion()).isEqualTo(version);
        assertThat(session.scannerHeaders()).containsEntry("Mcp-Session-Id", "session-2");
        assertThat(session.protocolHeaders()).containsEntry("Mcp-Session-Id", "session-2")
                .containsEntry("MCP-Protocol-Version", version);
        assertThat(session.nextRequestId()).isEqualTo(3);
        assertThat(initializations).hasValue(2);
        assertThat(refreshes).hasValue(1);
        assertThat(calls).hasSize(3);
    }

    @Test
    void modernCatalogRefreshPreservesAdmissionMetadataAndSkipsSessionRecovery() throws Exception {
        HttpClient client = mock(HttpClient.class);
        AtomicInteger admissions = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    if (request.headers().firstValue("Mcp-Method").orElse("").equals("server/discover")) {
                        admissions.incrementAndGet();
                        return response(200, "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"capabilities\":{},\"supportedVersions\":[\"2026-07-28\"]}}", null);
                    }
                    assertThat(request.headers().firstValue("Mcp-Method")).contains("tools/list");
                    if (calls.incrementAndGet() == 1) return response(401, "", null);
                    assertThat(request.headers().firstValue("Authorization")).contains("Bearer new");
                    assertThat(request.headers().firstValue("Mcp-Session-Id")).isEmpty();
                    return response(200, "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"tools\":[{\"name\":\"protected-tool\"}]}}", null);
                });
        McpScannerSession session = session(client);
        session.connect(new McpServerConfig("http://localhost/mcp", TransportType.MODERN_HTTP,
                rotatingAuth(refreshes)));

        assertThat(session.catalog("tools/list", null).result().toString()).contains("protected-tool");
        assertThat(session.admissionResult()).containsKey("supportedVersions");
        assertThat(admissions).hasValue(1);
        assertThat(calls).hasValue(2);
        assertThat(refreshes).hasValue(1);
    }

    @Test
    void catalogRecoveryBudgetIsIndependentForEachOperation() throws Exception {
        HttpClient client = mock(HttpClient.class);
        AtomicInteger refreshes = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    String payload = body(request);
                    if (payload.contains("\"method\":\"initialize\"")) {
                        return response(200, initializeReply("2025-11-25"), "session-valid");
                    }
                    if (payload.contains("tools/list")) {
                        if (calls.incrementAndGet() < 4) return response(401, "", null);
                        return response(200, "{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{\"tools\":[{\"name\":\"protected-tool\"}]}}", null);
                    }
                    return response(202, "", null);
                });
        McpScannerSession session = session(client);
        session.connect(new McpServerConfig("http://localhost/mcp", TransportType.STREAMABLE_HTTP,
                rotatingAuth(refreshes)));

        assertThatThrownBy(() -> session.catalog("tools/list", null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("HTTP 401");
        assertThat(calls).hasValue(2);
        assertThat(refreshes).hasValue(1);

        assertThat(session.catalog("tools/list", null).result().toString()).contains("protected-tool");
        assertThat(calls).hasValue(4);
        assertThat(refreshes).hasValue(2);
    }

    @Test
    void autoTransportDoesNotTrySseAfterHttpAuthenticationRefusal() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> response(401, "", null));
        McpScannerSession session = session(client);

        assertThatThrownBy(() -> session.connect(new McpServerConfig("http://localhost/mcp",
                TransportType.AUTO, new BearerTokenAuthStrategy("denied"))))
                .isInstanceOf(IllegalStateException.class);

        verify(client, never()).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        assertThat(session.resolvedEndpoint()).isNull();
        assertThat(session.scannerHeaders()).isEmpty();
    }

    @Test
    void autoTransportDoesNotTrySseWhenTheCredentialProviderFails() throws Exception {
        HttpClient client = mock(HttpClient.class);
        AuthStrategy auth = new AuthStrategy() {
            @Override public Map<String, String> headers() { return Map.of(); }
            @Override public java.util.Optional<CredentialProvider> sharedCredentialProvider() {
                return java.util.Optional.of(() -> { throw new IllegalStateException("private-token-value"); });
            }
        };
        McpScannerSession session = session(client);

        assertThatThrownBy(() -> session.connect(new McpServerConfig("http://localhost/mcp",
                TransportType.AUTO, auth)))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(ConnectionAuthException.class)
                .hasRootCauseMessage("Connection authentication requires attention: STOP");

        verify(client, never()).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        assertThat(session.resolvedEndpoint()).isNull();
        assertThat(session.scannerHeaders()).isEmpty();
    }

    @Test
    void disconnectSkipsDeleteAfterTheCredentialProviderRefusesCredentials() throws Exception {
        HttpClient client = mock(HttpClient.class);
        AtomicBoolean refused = new AtomicBoolean();
        AtomicInteger credentialReads = new AtomicInteger();
        AtomicInteger deletes = new AtomicInteger();
        CredentialProvider provider = () -> {
            credentialReads.incrementAndGet();
            if (refused.get()) throw new IllegalStateException("Credentials unavailable");
            return Map.of("Authorization", "Bearer current");
        };
        AuthStrategy auth = new AuthStrategy() {
            @Override public Map<String, String> headers() { return Map.of("Authorization", "Bearer cached"); }
            @Override public java.util.Optional<CredentialProvider> sharedCredentialProvider() {
                return java.util.Optional.of(provider);
            }
        };
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    if (request.method().equals("DELETE")) {
                        deletes.incrementAndGet();
                        return response(200, "", null);
                    }
                    if (body(request).contains("\"method\":\"initialize\"")) {
                        return response(200, initializeReply("2025-11-25"), "session-authenticated");
                    }
                    return response(202, "", null);
                });
        McpScannerSession session = session(client);
        session.connect(new McpServerConfig("http://localhost/mcp", TransportType.STREAMABLE_HTTP, auth));
        refused.set(true);
        assertThatThrownBy(session::scannerHeaders).isInstanceOf(ConnectionAuthException.class);
        int readsBeforeDisconnect = credentialReads.get();

        session.disconnect();

        assertThat(deletes).hasValue(0);
        assertThat(credentialReads).hasValue(readsBeforeDisconnect);
        assertThat(session.scannerHeaders()).isEmpty();
    }

    @Test
    void nestedInitializeArgumentDoesNotOverwriteAdmissionMetadata() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    String method = McpObjectMapper.INSTANCE.readTree(body(request)).path("method").asText();
                    if (method.equals("initialize")) {
                        return response(200, initializeReply("2025-11-25"), "session-valid");
                    }
                    if (method.equals("tools/call")) {
                        return response(200, "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"operation-output\"}]}}", null);
                    }
                    return response(202, "", null);
                });
        McpScannerSession session = session(client);
        session.connect(new McpServerConfig("http://localhost/mcp", TransportType.STREAMABLE_HTTP,
                new NoAuthStrategy()));

        var reply = session.sendHttpOperation(session.operation("tools/call",
                "{\"name\":\"demo\",\"arguments\":{\"method\":\"initialize\"}}", 2));

        assertThat(reply.body()).contains("operation-output");
        assertThat(session.admissionResult()).containsEntry("protocolVersion", "2025-11-25");
    }

    @Test
    void repeatedAuthenticateHeadersReachSharedScopeApprovalPolicy() throws Exception {
        HttpClient client = mock(HttpClient.class);
        var binding = new CredentialBinding(URI.create("https://issuer.example"),
                URI.create("http://localhost/mcp"), "client", Set.of("read"));
        var credential = new OAuthCredential(binding, "current", null,
                Instant.now().plusSeconds(3600), Set.of("read"));
        OAuthCredentialAccess access = mock(OAuthCredentialAccess.class);
        when(access.current()).thenReturn(Optional.of(credential));
        AtomicInteger approvals = new AtomicInteger();
        var provider = new OAuthCredentialProvider(binding, access, () -> {
            approvals.incrementAndGet();
            return AuthorizationApproval.REQUIRE_PROMPT;
        });
        AuthStrategy auth = mock(AuthStrategy.class);
        when(auth.headers()).thenReturn(Map.of("Authorization", "Bearer current"));
        when(auth.sharedCredentialProvider()).thenReturn(Optional.of(provider));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    String payload = body(request);
                    if (payload.contains("\"method\":\"initialize\"")) {
                        return response(200, initializeReply("2025-11-25"), "session-valid");
                    }
                    if (payload.contains("tools/list")) {
                        var denied = response(403, "", null);
                        when(denied.headers()).thenReturn(HttpHeaders.of(Map.of("WWW-Authenticate",
                                List.of("Basic realm=\"legacy,realm\"", "Bearer error=\"insufficient_scope\", scope=\"write\"")),
                                (name, value) -> true));
                        return denied;
                    }
                    return response(202, "", null);
                });
        McpScannerSession session = session(client);
        session.connect(new McpServerConfig("http://localhost/mcp", TransportType.STREAMABLE_HTTP, auth));

        assertThatThrownBy(() -> session.catalog("tools/list", null))
                .isInstanceOf(ConnectionAuthException.class).hasMessageContaining("APPROVAL_REQUIRED");
        assertThat(approvals).hasValue(1);
        verify(access, never()).refresh();
        verify(access, never()).authorize(any());
    }

    @ParameterizedTest
    @CsvSource({"401,false", "401,true", "403,false", "403,true"})
    void authenticationRefusalNeverFallsBackToSseWhenItsBodyFails(int status, boolean oversized) throws Exception {
        HttpClient client = mock(HttpClient.class);
        AtomicBoolean closed = new AtomicBoolean();
        InputStream refusedBody = oversized
                ? new ByteArrayInputStream(new byte[BoundedBodyReader.MAX_RESPONSE_BYTES + 1]) {
                    @Override public void close() { closed.set(true); }
                }
                : new InputStream() {
                    @Override public int read() throws IOException { throw new IOException("Body read failed"); }
                    @Override public void close() throws IOException {
                        closed.set(true);
                        throw new IOException("Body close failed");
                    }
                };
        HttpResponse<InputStream> denied = response(status, "", null);
        when(denied.body()).thenReturn(refusedBody);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(denied);
        McpScannerSession session = session(client);

        assertThatThrownBy(() -> session.connect(new McpServerConfig("http://localhost/mcp",
                TransportType.AUTO, new NoAuthStrategy())))
                .isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(ConnectionAuthException.class);

        verify(client, never()).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        assertThat(closed).isTrue();
        assertThat(session.resolvedEndpoint()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void disconnectUsesCurrentCredentialsOrSkipsDeleteOnNewRefusal(boolean refused) throws Exception {
        HttpClient client = mock(HttpClient.class);
        AtomicReference<String> token = new AtomicReference<>("initial");
        CredentialProvider provider = () -> {
            if (token.get() == null) throw new IllegalStateException("Credentials unavailable");
            return Map.of("Authorization", "Bearer " + token.get());
        };
        AuthStrategy auth = mock(AuthStrategy.class);
        when(auth.headers()).thenReturn(Map.of("Authorization", "Bearer cached"));
        when(auth.sharedCredentialProvider()).thenReturn(Optional.of(provider));
        List<String> deleteCredentials = new ArrayList<>();
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    if (request.method().equals("DELETE")) {
                        deleteCredentials.add(request.headers().firstValue("Authorization").orElse(""));
                        return response(200, "", null);
                    }
                    if (body(request).contains("\"method\":\"initialize\"")) {
                        return response(200, initializeReply("2025-11-25"), "session-valid");
                    }
                    return response(202, "", null);
                });
        McpScannerSession session = session(client);
        session.connect(new McpServerConfig("http://localhost/mcp", TransportType.STREAMABLE_HTTP, auth));
        token.set(refused ? null : "rotated");

        session.disconnect();

        if (refused) assertThat(deleteCredentials).isEmpty();
        else assertThat(deleteCredentials).containsExactly("Bearer rotated");
        assertThat(session.scannerHeaders()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-07-28", "2025-03-26", "2025-06-18", "2025-11-25"})
    void protocolHeadersExposeOnlyFramingWithoutReadingCredentials(String version) throws Exception {
        HttpClient client = mock(HttpClient.class);
        AtomicBoolean readsForbidden = new AtomicBoolean();
        AtomicInteger credentialReads = new AtomicInteger();
        AtomicInteger authReads = new AtomicInteger();
        CredentialProvider provider = () -> {
            if (readsForbidden.get()) throw new AssertionError("Credential read during protocol snapshot");
            credentialReads.incrementAndGet();
            return Map.of("Authorization", "Bearer provider-token");
        };
        AuthStrategy auth = new AuthStrategy() {
            @Override public Map<String, String> headers() {
                if (readsForbidden.get()) throw new AssertionError("Auth read during protocol snapshot");
                authReads.incrementAndGet();
                return Map.of("Authorization", "Bearer cached-token");
            }
            @Override public boolean isTerminallyFailed() {
                if (readsForbidden.get()) throw new AssertionError("Terminal auth read during protocol snapshot");
                return false;
            }
            @Override public Optional<CredentialProvider> sharedCredentialProvider() { return Optional.of(provider); }
        };
        boolean modern = version.equals("2026-07-28");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    if (modern) {
                        return response(200, "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"capabilities\":{},\"supportedVersions\":[\"2026-07-28\"]}}", null);
                    }
                    if (body(request).contains("\"method\":\"initialize\"")) {
                        return response(200, initializeReply(version), "session-" + version);
                    }
                    return response(202, "", null);
                });
        McpScannerSession session = session(client);
        assertThat(session.protocolHeaders()).isEmpty();
        session.connect(new McpServerConfig("http://localhost/mcp",
                modern ? TransportType.MODERN_HTTP : TransportType.STREAMABLE_HTTP, auth));
        credentialReads.set(0);
        authReads.set(0);
        readsForbidden.set(true);

        Map<String, String> headers = session.protocolHeaders();

        assertThat(headers).isEqualTo(modern ? Map.of("MCP-Protocol-Version", version)
                : Map.of("MCP-Protocol-Version", version, "Mcp-Session-Id", "session-" + version));
        assertThat(credentialReads).hasValue(0);
        assertThat(authReads).hasValue(0);
        assertThatThrownBy(() -> headers.put("Authorization", "injected"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static AuthStrategy rotatingAuth(AtomicInteger refreshes) {
        return new AuthStrategy() {
            @Override public Map<String, String> headers() {
                return Map.of("Authorization", "Bearer " + (refreshes.get() == 0 ? "old" : "new"));
            }
            @Override public boolean supportsRefresh() { return true; }
            @Override public boolean refresh() { refreshes.incrementAndGet(); return true; }
        };
    }

    @Test
    void statelessLegacySessionDoesNotSendDelete() throws Exception {
        HttpClient client = mock(HttpClient.class);
        List<HttpRequest> requests = new ArrayList<>();
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    requests.add(request);
                    if (body(request).contains("\"method\":\"initialize\"")) {
                        return response(200, initializeReply("2025-06-18"), null);
                    }
                    return response(202, "", null);
                });
        McpScannerSession session = session(client);

        session.connect(new McpServerConfig("http://localhost/mcp", TransportType.STREAMABLE_HTTP,
                new NoAuthStrategy()));
        assertThat(session.scannerHeaders()).doesNotContainKey("Mcp-Session-Id");
        session.disconnect();

        assertThat(requests).noneMatch(request -> request.method().equals("DELETE"));
    }

    @Test
    void scannerHeadersReturnsIndependentUnmodifiableSnapshots() throws Exception {
        McpScannerSession session = session(legacyClient());
        session.connect(new McpServerConfig("http://localhost/mcp", TransportType.STREAMABLE_HTTP,
                new NoAuthStrategy()));

        Map<String, String> first = session.scannerHeaders();
        Map<String, String> second = session.scannerHeaders();

        assertThat(first).isNotSameAs(second).containsEntry("Mcp-Session-Id", "session-abc");
        assertThatThrownBy(() -> first.put("Mcp-Session-Id", "replacement"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(session.scannerHeaders()).containsEntry("Mcp-Session-Id", "session-abc");
    }

    @Test
    void refusesCredentialsOverRemotePlainHttp() {
        McpScannerSession session = session(mock(HttpClient.class));
        assertThatThrownBy(() -> session.connect(new McpServerConfig(
                "http://remote.example/mcp", TransportType.AUTO, new BearerTokenAuthStrategy("secret"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cleartext");
    }

    @Test
    void rejectsSseWhenLocalProxyIsUnavailable() {
        McpScannerSession session = new McpScannerSession(mock(HttpClient.class), new McpEventLog(null), () -> false);
        assertThatThrownBy(() -> session.connect(new McpServerConfig(
                "http://localhost/sse", TransportType.SSE, new NoAuthStrategy())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("proxy unavailable");
    }

    @Test
    void alwaysModeUsesRedirectedSseOriginForInitialMessagePost() throws Exception {
        URI original = URI.create("http://localhost:3001/sse");
        URI reached = URI.create("http://127.0.0.1:3002/moved/stream");
        HttpClient client = mock(HttpClient.class);
        HttpResponse<InputStream> redirect = response(307, "", null);
        when(redirect.headers()).thenReturn(HttpHeaders.of(
                Map.of("Location", List.of(reached.toString())), (name, value) -> true));
        HttpResponse<InputStream> opened = response(200,
                "event: endpoint\ndata: message\n\n"
                        + "event: message\ndata: " + initializeReply("2024-11-05") + "\n\n", null);
        when(opened.uri()).thenReturn(reached);
        List<HttpRequest> posts = new ArrayList<>();
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    return CompletableFuture.completedFuture(request.uri().equals(original) ? redirect : opened);
                });
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    posts.add(request);
                    return response(202, "", null);
                });

        McpScannerSession session = session(client);
        AtomicReference<RedirectMode> redirectMode = new AtomicReference<>(RedirectMode.ALWAYS);
        session.setRedirectModeSupplier(redirectMode::get);
        session.connect(new McpServerConfig(original.toString(), TransportType.SSE,
                new BearerTokenAuthStrategy("test")));

        assertThat(posts).isNotEmpty().allMatch(request ->
                request.uri().toString().equals("http://127.0.0.1:3002/moved/message"));
        assertThat(posts).allMatch(request -> request.headers().firstValue("Authorization")
                .orElse("").equals("Bearer test"));
        assertThat(session.resolvedEndpoint()).isEqualTo("http://127.0.0.1:3002/moved/message");

        int postsBeforeModeChange = posts.size();
        redirectMode.set(RedirectMode.METHOD_PRESERVING_SAME_ORIGIN);
        assertThatThrownBy(() -> session.catalog("tools/list", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Reconnect");
        assertThat(posts).hasSize(postsBeforeModeChange);
        assertThat(session.resolvedEndpoint()).isNull();
    }

    @Test
    void modeChangeDuringSseHandshakeDoesNotKeepRedirectedEndpoint() throws Exception {
        URI original = URI.create("http://localhost:3001/sse");
        URI reached = URI.create("http://127.0.0.1:3002/moved/stream");
        HttpClient client = mock(HttpClient.class);
        HttpResponse<InputStream> redirect = response(307, "", null);
        when(redirect.headers()).thenReturn(HttpHeaders.of(
                Map.of("Location", List.of(reached.toString())), (name, value) -> true));
        HttpResponse<InputStream> opened = response(200,
                "event: endpoint\ndata: message\n\n"
                        + "event: message\ndata: " + initializeReply("2024-11-05") + "\n\n", null);
        when(opened.uri()).thenReturn(reached);
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(
                        ((HttpRequest) invocation.getArgument(0)).uri().equals(original) ? redirect : opened));
        AtomicReference<RedirectMode> mode = new AtomicReference<>(RedirectMode.ALWAYS);
        AtomicInteger posts = new AtomicInteger();
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    posts.incrementAndGet();
                    mode.set(RedirectMode.METHOD_PRESERVING_SAME_ORIGIN);
                    return response(202, "", null);
                });

        McpScannerSession session = session(client);
        session.setRedirectModeSupplier(mode::get);
        assertThatThrownBy(() -> session.connect(new McpServerConfig(original.toString(), TransportType.SSE,
                new BearerTokenAuthStrategy("test"))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(posts).hasValue(1);
        assertThat(session.resolvedEndpoint()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2025-03-26", "2025-06-18", "2025-11-25", "2026-07-28"})
    void httpAdmissionAndCatalogSkipServerRequestsAndUnrelatedSseReplies(String version) throws Exception {
        HttpClient client = mock(HttpClient.class);
        boolean modern = version.equals("2026-07-28");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    if (request.method().equals("DELETE")) return response(204, "", null);
                    String payload = body(request);
                    if (payload.contains("notifications/initialized")) return response(202, "", null);
                    String reply;
                    if (payload.contains("initialize")) reply = initializeReply(version);
                    else if (request.headers().firstValue("Mcp-Method").orElse("").equals("server/discover")) {
                        reply = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"capabilities\":{},\"supportedVersions\":[\"2026-07-28\"]}}";
                    } else {
                        var id = McpObjectMapper.INSTANCE.readTree(payload).get("id");
                        reply = "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"tools\":[{\"name\":\"correlated-tool\"}]}}";
                    }
                    String frames = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":77,\"method\":\"ping\"}\n\n"
                            + "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":88,\"result\":{}}\n\n"
                            + "event: message\ndata: " + reply + "\n\n";
                    HttpResponse<InputStream> stream = response(200, frames, null);
                    when(stream.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("text/event-stream")),
                            (name, value) -> true));
                    return stream;
                });
        McpScannerSession session = session(client);
        try {
            session.connect(new McpServerConfig("http://localhost/mcp",
                    modern ? TransportType.MODERN_HTTP : TransportType.STREAMABLE_HTTP, new NoAuthStrategy()));
            assertThat(session.catalog("tools/list", null).result().toString()).contains("correlated-tool");
        } finally {
            session.disconnect();
        }
    }

    private static HttpClient legacyClient() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    if (request.headers().firstValue("Mcp-Method").isPresent()) return response(404, "", null);
                    if (request.bodyPublisher().isPresent()
                            && body(request).contains("\"method\":\"initialize\"")) {
                        return response(200, initializeReply("2025-06-18"), "session-abc");
                    }
                    return response(202, "", null);
                });
        return client;
    }

    private static McpScannerSession session(HttpClient client) {
        return new McpScannerSession(client, new McpEventLog(null));
    }

    private static String initializeReply(String version) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"" + version
                + "\",\"capabilities\":{},\"serverInfo\":{\"name\":\"test\",\"version\":\"1\"}}}";
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<InputStream> response(int status, String body, String sessionId) {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        Map<String, List<String>> values = sessionId == null
                ? Map.of("Content-Type", List.of("application/json"))
                : Map.of("Content-Type", List.of("application/json"), "Mcp-Session-Id", List.of(sessionId));
        when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(HttpHeaders.of(values, (name, value) -> true));
        when(response.body()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        return response;
    }

    private static String body(HttpRequest request) {
        StringBuilder result = new StringBuilder();
        request.bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<>() {
            @Override public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(java.nio.ByteBuffer item) { result.append(StandardCharsets.UTF_8.decode(item)); }
            @Override public void onError(Throwable throwable) { throw new IllegalStateException(throwable); }
            @Override public void onComplete() { }
        });
        return result.toString();
    }
}
