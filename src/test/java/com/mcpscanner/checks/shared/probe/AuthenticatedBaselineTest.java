package com.mcpscanner.checks.shared.probe;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.CustomHeaderAuthStrategy;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.testutil.ImmutableHttpRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticatedBaselineTest {

    private static final HttpRequest SEED = ImmutableHttpRequest.post("http://mcp.example/mcp",
            Map.of("Mcp-Session-Id", "live"), SimulatedMcpServer.toolCall());

    @Test
    void carriesTheConfiguredCredentialsExplicitlySoTheProxyForwardsThemVerbatim() {
        HttpRequest baseline = new AuthenticatedBaseline(() -> new BearerTokenAuthStrategy("t")).requestFor(SEED);

        assertThat(baseline.headerValue("Authorization")).isEqualTo("Bearer t");
        assertThat(baseline.headerValue("X-Mcp-Scanner-Strip-Auth")).isEqualTo("1");
        assertThat(baseline.headerValue("Mcp-Session-Id")).isEqualTo("live");
        assertThat(baseline.bodyToString()).isEqualTo(SEED.bodyToString());
    }

    @Test
    void customHeaderCredentialsAreCarriedToo() {
        HttpRequest baseline = new AuthenticatedBaseline(() -> new CustomHeaderAuthStrategy(Map.of("X-Api-Key", "k")))
                .requestFor(SEED);

        assertThat(baseline.headerValue("X-Api-Key")).isEqualTo("k");
    }

    @Test
    void anonymousScanBaselineCarriesNoCredential() {
        HttpRequest baseline = new AuthenticatedBaseline(NoAuthStrategy::new).requestFor(SEED);

        assertThat(baseline.headerValue("Authorization")).isNull();
        assertThat(baseline.headerValue("X-Mcp-Scanner-Strip-Auth")).isEqualTo("1");
    }
}
