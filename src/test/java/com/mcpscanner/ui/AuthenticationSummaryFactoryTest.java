package com.mcpscanner.ui;

import com.mcpscanner.config.AuthTypeNames;

import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.auth.OAuthAuthCodeStrategy;
import com.mcpscanner.auth.grants.OptionalGrantSettings;
import com.mcpscanner.auth.oauth.AuthState;
import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.mcp.ServerMetadata;
import com.mcpscanner.ui.connect.ConnectionAttemptResult;
import com.mcpscanner.ui.state.ConnectAttempt;
import com.mcpscanner.core.oauth.ClientCredentialsAuthentication;
import com.mcpscanner.core.oauth.RegistrationMode;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticationSummaryFactoryTest {

    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String ENDPOINT = "https://mcp.example.com/mcp";
    private static final ConnectResult RESULT = new ConnectResult(List.of(), List.of(), List.of(), List.of(),
            ServerMetadata.empty());

    @Test
    void authorizationCodeSummaryComesFromTheLiveOAuthSession() {
        OAuthAuthCodeStrategy strategy = mock(OAuthAuthCodeStrategy.class);
        when(strategy.issuer()).thenReturn(URI.create("https://auth.example.com"));
        when(strategy.clientId()).thenReturn("client-123");
        when(strategy.registrationMode()).thenReturn(Optional.of(RegistrationMode.DCR));
        when(strategy.requestedScopes()).thenReturn(List.of("mcp:read", "mcp:write"));
        when(strategy.grantedScopes()).thenReturn(List.of("mcp:read"));
        when(strategy.snapshot()).thenReturn(new AuthState("alice", NOW.plusSeconds(600), true));
        ConnectAttempt attempt = ConnectAttempt.withOauth(ENDPOINT, TransportType.AUTO, "mcp.example.com",
                AuthTypeNames.OAUTH, null, URI.create(ENDPOINT));

        AuthenticationSummary summary = AuthenticationSummaryFactory.from(attempt,
                new ConnectionAttemptResult(RESULT, strategy), "User 1", issuer -> Optional.empty());

        assertThat(summary.rows(CLOCK))
                .containsEntry("Method", "OAuth 2.1 · Authorization code")
                .containsEntry("Issuer", "https://auth.example.com")
                .containsEntry("Client ID", "client-123")
                .containsEntry("Registration", "Dynamic Client Registration (DCR)")
                .containsEntry("Scopes requested", "mcp:read mcp:write")
                .containsEntry("Scopes granted", "mcp:read")
                .containsEntry("Token expires", "10:10 (in 10 min)");
    }

    @Test
    void autoAuthSummaryNamesWhatAutoResolvedTo() {
        OAuthAuthCodeStrategy strategy = mock(OAuthAuthCodeStrategy.class);
        when(strategy.issuer()).thenReturn(URI.create("https://auth.example.com"));
        when(strategy.clientId()).thenReturn("client-123");
        when(strategy.registrationMode()).thenReturn(Optional.of(RegistrationMode.DCR));
        when(strategy.requestedScopes()).thenReturn(List.of());
        ConnectAttempt oauthAttempt = ConnectAttempt.withAuto(ENDPOINT, TransportType.AUTO, "mcp.example.com",
                AuthTypeNames.AUTO, null, URI.create(ENDPOINT), null);
        ConnectAttempt openAttempt = ConnectAttempt.withAuto(ENDPOINT, TransportType.AUTO, "mcp.example.com",
                AuthTypeNames.AUTO, null, URI.create(ENDPOINT), null);

        AuthenticationSummary oauth = AuthenticationSummaryFactory.from(oauthAttempt,
                new ConnectionAttemptResult(RESULT, strategy, new NoAuthStrategy(),
                        "Detect → OAuth 2.1 (Authorization code, DCR)", null), "User 1", issuer -> Optional.empty());
        AuthenticationSummary open = AuthenticationSummaryFactory.from(openAttempt,
                new ConnectionAttemptResult(RESULT, null, new NoAuthStrategy(), "Detect → None", null), "User 1", issuer -> Optional.empty());

        assertThat(oauth.rows(CLOCK))
                .containsEntry("Method", "Detect → OAuth 2.1 (Authorization code, DCR)")
                .containsEntry("Issuer", "https://auth.example.com");
        assertThat(open.rows(CLOCK)).containsEntry("Method", "Detect → None");
    }

    @Test
    void optionalGrantSummaryReportsTheConfiguredClientAndRequestedScopes() {
        OptionalGrantSettings settings = new OptionalGrantSettings(OptionalGrantSettings.Profile.CLIENT_CREDENTIALS,
                URI.create("https://issuer.example"), "scanner", "secret", Set.of("b", "a"),
                ClientCredentialsAuthentication.basic(), null);
        ConnectAttempt attempt = ConnectAttempt.withOptionalGrant(ENDPOINT, TransportType.AUTO, "mcp.example.com",
                AuthTypeNames.OAUTH, settings);

        AuthenticationSummary summary = AuthenticationSummaryFactory.from(attempt,
                new ConnectionAttemptResult(RESULT, null, new NoAuthStrategy()), "User 1", issuer -> Optional.empty());

        assertThat(summary.rows(CLOCK))
                .containsEntry("Method", "OAuth 2.1 · Client credentials")
                .containsEntry("Issuer", "https://issuer.example")
                .containsEntry("Client ID", "scanner")
                .containsEntry("Scopes requested", "a b")
                .doesNotContainKey("Token expires");
    }

    @Test
    void staticCredentialSummaryNamesTheMethodOnly() {
        ConnectAttempt attempt = ConnectAttempt.withSnapshot(ENDPOINT, TransportType.AUTO, "mcp.example.com",
                AuthTypeNames.BEARER, new NoAuthStrategy());

        AuthenticationSummary summary = AuthenticationSummaryFactory.from(attempt,
                new ConnectionAttemptResult(RESULT, null, new NoAuthStrategy()), "User 1", issuer -> Optional.empty());

        assertThat(summary.rows(CLOCK)).containsOnlyKeys("Connected as", "Method")
                .containsEntry("Method", "Bearer Token");
    }

    @Test
    void staticBearerSummaryCarriesTheTokenButCannotRenewIt() {
        ConnectAttempt attempt = ConnectAttempt.withSnapshot(ENDPOINT, TransportType.AUTO, "mcp.example.com",
                AuthTypeNames.BEARER, new NoAuthStrategy());

        AuthenticationSummary summary = AuthenticationSummaryFactory.from(attempt,
                new ConnectionAttemptResult(RESULT, null, new com.mcpscanner.auth.BearerTokenAuthStrategy("abc")),
                "User 1", issuer -> Optional.empty());

        assertThat(summary.token()).hasValueSatisfying(token -> assertThat(token.value()).isEqualTo("abc"));
        assertThat(summary.renewable()).isFalse();
        assertThat(summary.rows(CLOCK)).doesNotContainKey("Token expires");
    }

    @Test
    void optionalGrantSummaryCarriesTheIssuedTokenExpiryAndCanRenew() {
        OptionalGrantSettings settings = new OptionalGrantSettings(OptionalGrantSettings.Profile.CLIENT_CREDENTIALS,
                URI.create("https://issuer.example"), "scanner", "secret", Set.of("a"),
                ClientCredentialsAuthentication.basic(), null);
        ConnectAttempt attempt = ConnectAttempt.withOptionalGrant(ENDPOINT, TransportType.AUTO, "mcp.example.com",
                AuthTypeNames.OAUTH, settings);
        com.mcpscanner.auth.AuthStrategy issued = new com.mcpscanner.auth.AuthStrategy() {
            @Override
            public java.util.Map<String, String> headers() {
                return java.util.Map.of();
            }

            @Override
            public boolean supportsRefresh() {
                return true;
            }

            @Override
            public java.util.Optional<com.mcpscanner.auth.AccessToken> currentToken() {
                return java.util.Optional.of(com.mcpscanner.auth.AccessToken.of("machine", NOW.plusSeconds(1200)));
            }
        };

        AuthenticationSummary summary = AuthenticationSummaryFactory.from(attempt,
                new ConnectionAttemptResult(RESULT, null, issued), "User 1", issuer -> Optional.empty());

        assertThat(summary.renewable()).isTrue();
        assertThat(summary.rows(CLOCK)).containsEntry("Token expires", "10:20 (in 20 min)");
    }
}
