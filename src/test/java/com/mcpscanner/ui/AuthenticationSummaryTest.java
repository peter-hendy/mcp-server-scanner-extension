package com.mcpscanner.ui;

import com.mcpscanner.auth.AccessToken;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.SignedInTokens;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticationSummaryTest {

    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void oauthSummaryNamesThePrimaryUserThenIssuerClientRegistrationScopesAndExpiry() {
        AuthenticationSummary summary = AuthenticationSummary.builder("OAuth 2.1 · Authorization code")
                .connectedAs("test")
                .issuer(URI.create("https://auth.example.com"))
                .clientId("client-123")
                .registration("Dynamic Client Registration (DCR)")
                .grantedScopes(List.of("openid", "mcp:read"))
                .tokenExpiry(() -> Optional.of(NOW.plusSeconds(45 * 60)))
                .build();

        Map<String, String> rows = summary.rows(CLOCK);

        assertThat(rows).containsExactly(
                Map.entry("Connected as", "test (primary user)"),
                Map.entry("Method", "OAuth 2.1 · Authorization code"),
                Map.entry("Issuer", "https://auth.example.com"),
                Map.entry("Client ID", "client-123"),
                Map.entry("Registration", "Dynamic Client Registration (DCR)"),
                Map.entry("Scopes granted", "openid mcp:read"),
                Map.entry("Token expires", "10:45 (in 45 min)"));
    }

    @Test
    void staticCredentialSummaryOmitsTheOAuthRows() {
        AuthenticationSummary summary = AuthenticationSummary.builder("Bearer Token").build();

        assertThat(summary.rows(CLOCK)).containsOnlyKeys("Method");
    }

    @Test
    void oauthWithoutGrantedScopesOrExpirySaysSo() {
        AuthenticationSummary summary = AuthenticationSummary.builder("OAuth 2.1")
                .issuer(URI.create("https://auth.example.com"))
                .grantedScopes(List.of())
                .tokenExpiry(Optional::empty)
                .build();

        assertThat(summary.rows(CLOCK))
                .containsEntry("Scopes granted", "None reported")
                .containsEntry("Token expires", "Not reported");
    }

    @Test
    void aHeldRefreshTokenSaysTheTokenRenews() {
        AuthStrategy session = mock(AuthStrategy.class);
        when(session.signedInTokens()).thenReturn(Optional.of(new SignedInTokens(Optional.empty(),
                AccessToken.of("access-1", NOW.plusSeconds(600)), Optional.of("refresh-1"))));
        AuthenticationSummary summary = AuthenticationSummary.builder("OAuth 2.1")
                .tokenExpiry(() -> Optional.of(NOW.plusSeconds(600)))
                .credential(session)
                .build();

        assertThat(summary.rows(CLOCK))
                .containsEntry("Token expires", "10:10 (in 10 min) · renews with the refresh token");
    }

    @Test
    void anExpiredTokenSaysItHasExpired() {
        AuthenticationSummary summary = AuthenticationSummary.builder("OAuth 2.1")
                .tokenExpiry(() -> Optional.of(NOW.minusSeconds(60)))
                .build();

        assertThat(summary.rows(CLOCK)).containsEntry("Token expires", "09:59 (expired)");
    }
}
