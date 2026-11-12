package com.mcpscanner.client;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.core.connection.CredentialProvider;
import com.mcpscanner.core.connection.ConnectionAuthException;
import com.mcpscanner.core.connection.OAuthCredentialAccess;
import com.mcpscanner.core.connection.OAuthCredentialProvider;
import com.mcpscanner.core.oauth.AuthorizationApproval;
import com.mcpscanner.core.oauth.CredentialBinding;
import com.mcpscanner.core.oauth.OAuthCredential;
import com.mcpscanner.core.connection.OperationRecovery;
import com.mcpscanner.core.connection.RecoveryResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class McpScannerSessionSseRecoveryTest {
    @Test
    void getUnauthorizedRefreshesBeforeOpeningAuthenticatedStream() throws Exception {
        HttpClient client = mock(HttpClient.class);
        RotatingCredentials credentials = new RotatingCredentials();
        List<HttpRequest> gets = new ArrayList<>();
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    gets.add(invocation.getArgument(0));
                    return CompletableFuture.completedFuture(gets.size() == 1
                            ? response(401, "hostile-secret", Map.of("WWW-Authenticate", List.of("Bearer error=\"invalid_token\"")))
                            : stream("/messages"));
                });
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> response(202, "", Map.of()));
        McpScannerSession session = session(client);
        try {
            session.connect(config(credentials));
            assertThat(gets).hasSize(2);
            assertThat(gets.get(0).headers().firstValue("Authorization")).contains("Bearer old");
            assertThat(gets.get(1).headers().firstValue("Authorization")).contains("Bearer new");
            assertThat(credentials.challenges).containsExactly(401);
            assertThat(session.negotiatedProtocolVersion()).isEqualTo("2024-11-05");
            assertThat(session.admissionResult()).containsKey("serverInfo");
        } finally {
            session.disconnect();
        }
    }

    @Test
    void catalogUnauthorizedClosesStaleStreamAndReinitializesWithFreshCredentials() throws Exception {
        HttpClient client = mock(HttpClient.class);
        RotatingCredentials credentials = new RotatingCredentials();
        List<HttpRequest> gets = new ArrayList<>();
        List<HttpRequest> posts = new ArrayList<>();
        HttpResponse<InputStream> stale = stream("/old-messages");
        InputStream staleBody = spy(stale.body());
        when(stale.body()).thenReturn(staleBody);
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    gets.add(invocation.getArgument(0));
                    return CompletableFuture.completedFuture(gets.size() == 1 ? stale : stream("/new-messages"));
                });
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    posts.add(invocation.getArgument(0));
                    return response(posts.size() == 3 ? 401 : 202, "", Map.of());
                });
        McpScannerSession session = session(client);
        try {
            session.connect(config(credentials));
            assertThat(session.catalog("tools/list", null).result()).containsEntry("tools", List.of());
            assertThat(gets).hasSize(2);
            assertThat(posts).hasSize(6);
            assertThat(posts.subList(3, 6)).allSatisfy(request -> {
                assertThat(request.uri().getPath()).isEqualTo("/new-messages");
                assertThat(request.headers().allValues("Authorization")).containsExactly("Bearer new");
            });
            assertThat(session.resolvedEndpoint()).endsWith("/new-messages");
            verify(staleBody).close();
            assertThat(credentials.challenges).containsExactly(401);
        } finally {
            session.disconnect();
        }
    }

    @ParameterizedTest
    @CsvSource({"false,DENIED", "true,DENIED", "false,REQUIRE_PROMPT", "true,REQUIRE_PROMPT"})
    void insufficientScopeReachesSharedApprovalPolicyOnGetAndCatalog(boolean refuseGet,
                                                                     AuthorizationApproval approval) throws Exception {
        HttpClient client = mock(HttpClient.class);
        CredentialBinding binding = new CredentialBinding(URI.create("https://issuer.example"),
                URI.create("http://localhost:8080/sse"), "client", Set.of("read"));
        OAuthCredentialAccess access = mock(OAuthCredentialAccess.class);
        when(access.current()).thenReturn(Optional.of(new OAuthCredential(binding, "current", null,
                Instant.now().plusSeconds(3600), Set.of("read"))));
        AtomicInteger approvals = new AtomicInteger();
        CredentialProvider credentials = new OAuthCredentialProvider(binding, access, scopes -> {
            assertThat(scopes).contains("write");
            approvals.incrementAndGet();
            return approval;
        });
        HttpResponse<InputStream> denied = response(403, "hostile-secret", Map.of("WWW-Authenticate",
                List.of("Basic realm=\"legacy,realm\"", "Bearer error=\"insufficient_scope\", scope=\"write\"")));
        InputStream refusedBody = spy(denied.body());
        when(denied.body()).thenReturn(refusedBody);
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(refuseGet ? denied : stream("/messages")));
        AtomicInteger posts = new AtomicInteger();
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> posts.incrementAndGet() == 3 ? denied : response(202, "", Map.of()));
        McpScannerSession session = session(client);
        try {
            if (refuseGet) {
                assertThatThrownBy(() -> session.connect(config(credentials)))
                        .hasCauseInstanceOf(ConnectionAuthException.class)
                        .hasStackTraceContaining(approval == AuthorizationApproval.DENIED ? "DENIED" : "APPROVAL_REQUIRED")
                        .hasStackTraceContaining("Failed to establish scanner session")
                        .satisfies(McpScannerSessionSseRecoveryTest::assertNoSecret);
                assertThat(posts).hasValue(0);
            } else {
                session.connect(config(credentials));
                assertThatThrownBy(() -> session.catalog("tools/list", null))
                        .isInstanceOf(ConnectionAuthException.class).hasMessageContaining(approval == AuthorizationApproval.DENIED ? "DENIED" : "APPROVAL_REQUIRED")
                        .satisfies(McpScannerSessionSseRecoveryTest::assertNoSecret);
                assertThat(posts).hasValue(3);
            }
            assertThat(approvals).hasValue(1);
            verify(access, never()).refresh();
            verify(access, never()).authorize(any());
            verify(refusedBody).close();
            verify(refusedBody, never()).read();
        } finally {
            session.disconnect();
        }
    }

    @Test
    void externallyRotatedCredentialsReplaceStreamBeforeNextCatalogPost() throws Exception {
        HttpClient client = mock(HttpClient.class);
        RotatingCredentials credentials = new RotatingCredentials();
        List<HttpRequest> gets = new ArrayList<>();
        List<HttpRequest> posts = new ArrayList<>();
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    gets.add(invocation.getArgument(0));
                    return CompletableFuture.completedFuture(stream("/messages-" + gets.size()));
                });
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    posts.add(invocation.getArgument(0));
                    return response(202, "", Map.of());
                });
        McpScannerSession session = session(client);
        try {
            session.connect(config(credentials));
            credentials.token = "rotated";
            session.catalog("tools/list", null);
            assertThat(gets).hasSize(2);
            assertThat(posts).hasSize(5);
            assertThat(gets.get(1).headers().firstValue("Authorization")).contains("Bearer rotated");
            assertThat(posts.subList(2, 5)).allSatisfy(request -> {
                assertThat(request.uri().getPath()).isEqualTo("/messages-2");
                assertThat(request.headers().allValues("Authorization")).containsExactly("Bearer rotated");
            });
            assertThat(credentials.challenges).isEmpty();
        } finally {
            session.disconnect();
        }
    }

    @Test
    void approvedScopeChallengeReopensGetAndHandshakeBeforeRetryingCatalog() throws Exception {
        HttpClient client = mock(HttpClient.class);
        CredentialBinding binding = new CredentialBinding(URI.create("https://issuer.example"),
                URI.create("http://localhost:8080/sse"), "client", Set.of("read"));
        var credential = new java.util.concurrent.atomic.AtomicReference<>(new OAuthCredential(binding,
                "old", null, Instant.now().plusSeconds(3600), Set.of("read")));
        List<Set<String>> authorizations = new ArrayList<>();
        OAuthCredentialAccess access = new OAuthCredentialAccess() {
            @Override public Optional<OAuthCredential> current() { return Optional.of(credential.get()); }
            @Override public boolean refreshAvailable() { return false; }
            @Override public void refresh() { throw new AssertionError("Unexpected refresh"); }
            @Override public void authorize(Set<String> scopes) {
                authorizations.add(Set.copyOf(scopes));
                credential.set(new OAuthCredential(binding, "authorized", null,
                        Instant.now().plusSeconds(3600), scopes));
            }
        };
        CredentialProvider credentials = new OAuthCredentialProvider(binding, access,
                () -> AuthorizationApproval.APPROVED);
        List<HttpRequest> gets = new ArrayList<>();
        List<HttpRequest> posts = new ArrayList<>();
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    gets.add(invocation.getArgument(0));
                    return CompletableFuture.completedFuture(stream("/messages-" + gets.size()));
                });
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    posts.add(invocation.getArgument(0));
                    return posts.size() == 3
                            ? response(403, "", Map.of("WWW-Authenticate", List.of(
                                    "Bearer error=\"insufficient_scope\", scope=\"write\"")))
                            : response(202, "", Map.of());
                });
        McpScannerSession session = session(client);
        try {
            session.connect(config(credentials));
            assertThat(session.catalog("tools/list", null).result()).containsEntry("tools", List.of());
            assertThat(authorizations).containsExactly(Set.of("read", "write"));
            assertThat(gets).hasSize(2);
            assertThat(gets.get(1).headers().firstValue("Authorization")).contains("Bearer authorized");
            assertThat(posts).hasSize(6);
            assertThat(posts.subList(3, 6)).allSatisfy(request -> {
                assertThat(request.uri().getPath()).isEqualTo("/messages-2");
                assertThat(request.headers().allValues("Authorization")).containsExactly("Bearer authorized");
            });
        } finally {
            session.disconnect();
        }
    }

    @Test
    void repeatedGetChallengesStopAfterSharedRetryBudget() {
        HttpClient client = mock(HttpClient.class);
        RotatingCredentials credentials = new RotatingCredentials();
        AtomicInteger gets = new AtomicInteger();
        when(client.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    gets.incrementAndGet();
                    return CompletableFuture.completedFuture(response(401, "hostile-secret", Map.of()));
                });
        McpScannerSession session = session(client);
        assertThatThrownBy(() -> session.connect(config(credentials)))
                .isInstanceOf(IllegalStateException.class).satisfies(McpScannerSessionSseRecoveryTest::assertNoSecret);
        assertThat(gets).hasValue(3);
        assertThat(credentials.challenges).containsExactly(401, 401);
        assertThat(session.resolvedEndpoint()).isNull();
    }

    private static void assertNoSecret(Throwable failure) {
        java.io.StringWriter output = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(output));
        assertThat(output.toString()).doesNotContain("hostile-secret");
    }

    private static McpScannerSession session(HttpClient client) {
        return new McpScannerSession(client, McpEventLog.noop());
    }

    private static McpServerConfig config(CredentialProvider credentials) {
        AuthStrategy auth = new AuthStrategy() {
            @Override public Map<String, String> headers() { return credentials.headers(); }
            @Override public Optional<CredentialProvider> sharedCredentialProvider() {
                return Optional.of(credentials);
            }
        };
        return new McpServerConfig("http://localhost:8080/sse", TransportType.SSE, auth);
    }

    private static HttpResponse<InputStream> stream(String endpoint) {
        return response(200, "event: endpoint\ndata: " + endpoint + "\n\n"
                + "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"serverInfo\":{\"name\":\"fixture\",\"version\":\"1\"}}}\n\n"
                + "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"tools\":[]}}\n\n", Map.of());
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<InputStream> response(int status, String body, Map<String, List<String>> headers) {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        when(response.headers()).thenReturn(HttpHeaders.of(headers, (name, value) -> true));
        return response;
    }

    private static final class RotatingCredentials implements CredentialProvider {
        private String token = "old";
        private final List<Integer> challenges = new ArrayList<>();
        @Override public Map<String, String> headers() { return Map.of("Authorization", "Bearer " + token); }
        @Override public OperationRecovery beginOperation(String operation) {
            return response -> {
                challenges.add(response.status());
                token = "new";
                return RecoveryResult.RETRY;
            };
        }
    }
}
