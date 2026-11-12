package com.mcpscanner.auth;

import com.mcpscanner.auth.oauth.TestOAuthStrategies;
import com.mcpscanner.auth.oauth.TestOAuthStrategies.Client;
import com.mcpscanner.auth.oauth.TestOAuthStrategies.Login;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.core.oauth.RegistrationMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthAuthCodeStrategySignedInTokensTest {

    private static final URI ISSUER = URI.create("https://issuer.example.com");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Client CLIENT = new Client("client-1", null, "none", RegistrationMode.DCR);

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void aSignedInSessionShowsItsAccessAndRefreshTokens() {
        OAuthAuthCodeStrategy strategy = signedIn("refresh-1");

        SignedInTokens tokens = strategy.signedInTokens().orElseThrow();

        assertThat(tokens.accessToken().value()).isEqualTo("access-1");
        assertThat(tokens.accessToken().expiresAt()).contains(NOW.plusSeconds(600));
        assertThat(tokens.refreshToken()).contains("refresh-1");
    }

    @Test
    void aSessionWithoutARefreshTokenShowsNone() {
        assertThat(signedIn(null).signedInTokens().orElseThrow().refreshToken()).isEmpty();
    }

    @Test
    void otherStrategiesHaveNoSignedInTokens() {
        assertThat(new BearerTokenAuthStrategy("t").signedInTokens()).isEmpty();
        assertThat(new NoAuthStrategy().signedInTokens()).isEmpty();
    }

    private static OAuthAuthCodeStrategy signedIn(String refreshToken) {
        return TestOAuthStrategies.authenticated(new Login(ISSUER, URI.create("https://mcp.example.com/mcp"),
                List.of("mcp:tools"), CLIENT, "access-1", refreshToken, NOW.plusSeconds(600),
                Clock.fixed(NOW, ZoneOffset.UTC)), TestOAuthStrategies.refusingRefresh());
    }
}
