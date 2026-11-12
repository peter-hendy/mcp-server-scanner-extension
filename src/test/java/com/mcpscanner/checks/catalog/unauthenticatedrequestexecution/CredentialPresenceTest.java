package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.CustomHeaderAuthStrategy;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.testutil.ImmutableHttpRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialPresenceTest {

    private static final burp.api.montoya.http.message.requests.HttpRequest PLAIN =
            ImmutableHttpRequest.post("http://mcp.example/mcp", Map.of(), "{}");

    @Test
    void anAuthStrategyThatContributesHeadersMeansCredentialsAreConfigured() {
        assertThat(CredentialPresence.configured(new BearerTokenAuthStrategy("t"), PLAIN)).isTrue();
        assertThat(CredentialPresence.configured(new CustomHeaderAuthStrategy(Map.of("X-Api-Key", "k")), PLAIN))
                .isTrue();
    }

    @Test
    void credentialHeadersOnTheSeedAlsoCount() {
        assertThat(CredentialPresence.configured(new NoAuthStrategy(),
                ImmutableHttpRequest.post("http://mcp.example/mcp", Map.of("authorization", "Bearer x"), "{}")))
                .isTrue();
        assertThat(CredentialPresence.configured(new NoAuthStrategy(),
                ImmutableHttpRequest.post("http://mcp.example/mcp", Map.of("Cookie", "AWSALB=sticky"), "{}"))).isFalse();
    }

    @Test
    void noStrategyHeadersAndNoCredentialHeadersMeansAnonymousScan() {
        assertThat(CredentialPresence.configured(new NoAuthStrategy(), PLAIN)).isFalse();
    }
}
