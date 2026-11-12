package com.mcpscanner.ui;

import com.mcpscanner.auth.AccessToken;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.OAuthAuthCodeStrategy;
import com.mcpscanner.auth.SignedInTokens;
import com.mcpscanner.auth.oauth.SignInEvidence;
import com.mcpscanner.auth.oauth.discovery.DiscoveredMetadata;
import com.mcpscanner.auth.oauth.discovery.DiscoverySource;
import com.mcpscanner.ui.serverinfo.AuthDiscoveryReport;
import com.nimbusds.oauth2.sdk.as.AuthorizationServerMetadata;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthDiscoveryReportsTest {

    private static final String ENDPOINT = "https://mcp.example/mcp";
    private static final Instant EXPIRY = Instant.parse("2026-11-11T15:00:00Z");

    private final DiscoveredMetadata discovered = new DiscoveredMetadata(URI.create("https://auth.example"),
            DiscoverySource.PRM_WELL_KNOWN, mock(AuthorizationServerMetadata.class));
    private final AuthDiscoveryReports reports = new AuthDiscoveryReports(endpoint ->
            endpoint.equals(URI.create(ENDPOINT)) ? Optional.of(discovered) : Optional.empty());

    @Test
    void anOAuthConnectionReportsTheDiscoveryItsSignInAndTheTokenExpiry() {
        SignInEvidence evidence = new SignInEvidence("{}", Optional.of("{\"client_id\":\"c\"}"));
        OAuthAuthCodeStrategy strategy = mock(OAuthAuthCodeStrategy.class);
        when(strategy.signInEvidence()).thenReturn(Optional.of(evidence));
        when(strategy.signedInTokens()).thenReturn(Optional.of(new SignedInTokens(Optional.empty(),
                AccessToken.of("token", EXPIRY), Optional.empty())));

        AuthDiscoveryReport report = reports.after(" " + ENDPOINT + " ", strategy);

        assertThat(report).isEqualTo(new AuthDiscoveryReport(Optional.of(discovered), Optional.of(evidence),
                Optional.of(EXPIRY)));
    }

    @Test
    void aNonOAuthConnectionStillReportsADiscoveryForItsEndpoint() {
        AuthDiscoveryReport report = reports.after(ENDPOINT, new BearerTokenAuthStrategy("t"));

        assertThat(report).isEqualTo(new AuthDiscoveryReport(Optional.of(discovered), Optional.empty(),
                Optional.empty()));
    }

    @Test
    void aNonOAuthConnectionToAnUndiscoveredEndpointReportsNothing() {
        AuthDiscoveryReport report = reports.after("https://other.example/mcp", new BearerTokenAuthStrategy("t"));

        assertThat(report.isEmpty()).isTrue();
    }
}
