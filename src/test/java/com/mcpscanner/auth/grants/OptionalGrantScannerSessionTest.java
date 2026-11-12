package com.mcpscanner.auth.grants;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.oauth.BurpHttpRequestSender;
import com.mcpscanner.auth.oauth.safety.SuspiciousDestinationGate;
import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.client.McpServerConfig;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.AuthenticatedMcpServer;
import com.mcpscanner.testutil.AuthenticatedMcpServer.McpCall;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.MutableClock;
import com.mcpscanner.testutil.RecordingRealHttp;
import com.mcpscanner.core.oauth.ClientCredentialsAuthentication;
import com.mcpscanner.core.oauth.EnterpriseIdentityProvider;
import com.mcpscanner.core.oauth.EnterpriseIdentityRequest;
import com.mcpscanner.core.oauth.OAuthFlowException;
import com.mcpscanner.core.oauth.TokenResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OptionalGrantScannerSessionTest {
    private static final String PROVIDER_ID = "organization";
    private static final String IDENTITY_ASSERTION = "identity-assertion";
    private static final String JWT_BEARER = "urn:ietf:params:oauth:grant-type:jwt-bearer";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-04T12:00:00Z"));
    private final List<EnterpriseIdentityRequest> identityRequests = new CopyOnWriteArrayList<>();
    private AuthenticatedMcpServer server;
    private McpScannerSession session;

    @BeforeEach
    void startServer() throws IOException {
        MontoyaTestFactory.install();
        server = AuthenticatedMcpServer.start();
        server.requireIssuedAccessToken();
        session = new McpScannerSession(HttpClient.newHttpClient(), new McpEventLog(null));
    }

    @AfterEach
    void stopServer() {
        session.disconnect();
        server.close();
    }

    @ParameterizedTest
    @EnumSource(OptionalGrantSettings.Profile.class)
    void discoveredGrantAuthenticatesEveryScannerRequest(OptionalGrantSettings.Profile profile) {
        AuthStrategy strategy = createStrategy(profile);

        connect(strategy);

        assertThat(catalog()).contains(AuthenticatedMcpServer.TOOL_NAME);
        assertThat(authenticatedCalls()).isNotEmpty()
                .allSatisfy(call -> assertThat(call.header("Authorization")).isEqualTo("Bearer access-1"));
        assertThat(server.tokenCalls()).singleElement().satisfies(call -> {
            assertThat(call.form()).containsAllEntriesOf(expectedTokenForm(profile));
            assertThat(call.authorization()).startsWith("Basic ");
        });
        assertThat(identityRequests).hasSize(profile == OptionalGrantSettings.Profile.ENTERPRISE_MANAGED ? 1 : 0)
                .allSatisfy(request -> assertThat(request.form()).containsEntry("resource", server.endpoint()));
    }

    @ParameterizedTest
    @EnumSource(OptionalGrantSettings.Profile.class)
    void revokedAccessTokenIsRegrantedOnChallengeAndTheRequestRetried(OptionalGrantSettings.Profile profile) {
        connect(createStrategy(profile));
        server.revokeIssuedAccessTokens();

        assertThat(catalog()).contains(AuthenticatedMcpServer.TOOL_NAME);

        assertThat(lastCall().header("Authorization")).isEqualTo("Bearer access-2");
        assertThat(server.tokenCalls()).hasSize(2)
                .allSatisfy(call -> assertThat(call.form()).containsAllEntriesOf(expectedTokenForm(profile)));
    }

    @ParameterizedTest
    @EnumSource(OptionalGrantSettings.Profile.class)
    void expiringAccessTokenIsRenewedBeforeTheNextRequestWithoutAChallenge(OptionalGrantSettings.Profile profile) {
        server.issueTokensValidFor(120);
        connect(createStrategy(profile));
        int challengesBeforeExpiry = challengeCount();
        clock.advance(Duration.ofSeconds(100));

        assertThat(catalog()).contains(AuthenticatedMcpServer.TOOL_NAME);

        assertThat(lastCall().header("Authorization")).isEqualTo("Bearer access-2");
        assertThat(challengeCount()).isEqualTo(challengesBeforeExpiry);
        assertThat(server.tokenCalls()).hasSize(2);
    }

    @ParameterizedTest
    @EnumSource(OptionalGrantSettings.Profile.class)
    void credentialBoundToOneEndpointCannotBeAimedAtAnother(OptionalGrantSettings.Profile profile) {
        AuthStrategy strategy = createStrategy(profile);
        int callsBefore = server.mcpCalls().size();

        for (String otherEndpoint : List.of(server.origin() + "/other", server.origin() + "/mcp/child",
                "http://localhost:" + URI.create(server.origin()).getPort() + "/mcp")) {
            assertThatThrownBy(() -> new McpServerConfig(otherEndpoint, TransportType.MODERN_HTTP, strategy))
                    .isInstanceOf(OAuthFlowException.class);
        }
        assertThat(server.mcpCalls()).hasSize(callsBefore);
        assertThat(server.tokenCalls()).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(OptionalGrantSettings.Profile.class)
    void deniedGrantFailsBeforeAnyCredentialReachesTheMcpServer(OptionalGrantSettings.Profile profile) {
        server.denyTokenRequests();

        assertThatThrownBy(() -> createStrategy(profile)).isInstanceOf(OAuthFlowException.class);

        assertThat(server.tokenCalls()).hasSize(1);
        assertThat(server.mcpCalls()).allSatisfy(call -> assertThat(call.header("Authorization")).isNull());
    }

    private AuthStrategy createStrategy(OptionalGrantSettings.Profile profile) {
        BurpHttpRequestSender sender = new BurpHttpRequestSender(new RecordingRealHttp());
        SuspiciousDestinationGate allowLoopbackFixture = (url, purpose) -> SuspiciousDestinationGate.Decision.allow();
        EnterpriseIdentityProviderRegistry providers = new EnterpriseIdentityProviderRegistry(Map.of(PROVIDER_ID, identityProvider()));
        return new OptionalGrantAuthFactory(sender, allowLoopbackFixture, providers, clock)
                .create(URI.create(server.endpoint()), settings(profile));
    }

    private OptionalGrantSettings settings(OptionalGrantSettings.Profile profile) {
        String providerId = profile == OptionalGrantSettings.Profile.ENTERPRISE_MANAGED ? PROVIDER_ID : null;
        return new OptionalGrantSettings(profile, URI.create(server.origin()), "scanner", "client-secret",
                Set.of(), ClientCredentialsAuthentication.basic(), providerId);
    }

    private EnterpriseIdentityProvider identityProvider() {
        return request -> {
            identityRequests.add(request);
            return new TokenResponse(200, Map.of("access_token", IDENTITY_ASSERTION, "token_type", "N_A",
                    "expires_in", 3600, "issued_token_type", "urn:ietf:params:oauth:token-type:id-jag"));
        };
    }

    private Map<String, String> expectedTokenForm(OptionalGrantSettings.Profile profile) {
        return profile == OptionalGrantSettings.Profile.ENTERPRISE_MANAGED
                ? Map.of("grant_type", JWT_BEARER, "assertion", IDENTITY_ASSERTION)
                : Map.of("grant_type", "client_credentials", "resource", server.endpoint());
    }

    private void connect(AuthStrategy strategy) {
        session.connect(new McpServerConfig(server.endpoint(), TransportType.MODERN_HTTP, strategy));
    }

    private String catalog() {
        return session.catalog("tools/list", null).result().toString();
    }

    private List<McpCall> authenticatedCalls() {
        return server.mcpCalls().stream().filter(call -> call.status() != 401).toList();
    }

    private McpCall lastCall() {
        List<McpCall> calls = server.mcpCalls();
        return calls.get(calls.size() - 1);
    }

    private int challengeCount() {
        return (int) server.mcpCalls().stream().filter(call -> call.status() == 401).count();
    }
}
