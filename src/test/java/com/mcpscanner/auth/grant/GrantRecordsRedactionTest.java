package com.mcpscanner.auth.grant;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GrantRecordsRedactionTest {

    private static final OAuthClient CLIENT =
            new OAuthClient("client-1", Optional.of("client-secret-value"), ClientType.CONFIDENTIAL_CONFIGURED);

    @Test
    void clientNeverPrintsItsSecret() {
        assertThat(CLIENT.toString()).contains("client-1").doesNotContain("client-secret-value");
    }

    @Test
    void grantNeverPrintsItsRefreshTokenOrSecret() {
        RefreshGrant grant = new RefreshGrant(URI.create("https://as.example"), Optional.empty(), CLIENT,
                "refresh-token-value", List.of("mcp:tools"));

        assertThat(grant.toString()).doesNotContain("refresh-token-value").doesNotContain("client-secret-value");
    }

    @Test
    void issuedTokensNeverPrintTheirValues() {
        IssuedTokens tokens = new IssuedTokens("access-value", Optional.of("refresh-value"), Duration.ofMinutes(5));

        assertThat(tokens.toString()).doesNotContain("access-value").doesNotContain("refresh-value");
    }

    @Test
    void requestedScopesAreCopied() {
        List<String> scopes = new java.util.ArrayList<>(List.of("a"));
        RefreshGrant grant = new RefreshGrant(URI.create("https://as.example"), Optional.empty(), CLIENT, "rt", scopes);
        scopes.add("b");

        assertThat(grant.requestedScopes()).containsExactly("a");
    }
}
