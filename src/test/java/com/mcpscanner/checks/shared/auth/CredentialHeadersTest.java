package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.CustomHeaderAuthStrategy;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.testutil.ImmutableHttpRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialHeadersTest {

    private static final String URL = "http://127.0.0.1:8000/mcp";

    @Test
    void anAuthenticatedSeedCarriesCredentials() {
        assertThat(new CredentialHeaders(NoAuthStrategy::new).carriedBy(Seeds.authenticated(URL))).isTrue();
    }

    @Test
    void aRequestWithOnlyTransportHeadersCarriesNoCredentials() {
        assertThat(new CredentialHeaders(NoAuthStrategy::new).carriedBy(anonymous())).isFalse();
    }

    @Test
    void aCustomStrategyHeaderCountsAsACredential() {
        HttpRequest request = anonymous().withAddedHeader("X-Tenant-Key", "k");

        assertThat(new CredentialHeaders(() -> new CustomHeaderAuthStrategy(Map.of("X-Tenant-Key", "k")))
                .carriedBy(request)).isTrue();
    }

    @Test
    void aSessionIdAloneIsNotACredential() {
        assertThat(new CredentialHeaders(NoAuthStrategy::new)
                .carriedBy(anonymous().withAddedHeader("Mcp-Session-Id", "s"))).isFalse();
    }

    @Test
    void addsTheStrategyHeadersTheRequestLacks() {
        HttpRequest supplemented = new CredentialHeaders(() -> new BearerTokenAuthStrategy("fresh"))
                .withMissingAdded(anonymous());

        assertThat(supplemented.headerValue("Authorization")).isEqualTo("Bearer fresh");
    }

    @Test
    void keepsAuthHeadersTheRequestAlreadyCarries() {
        HttpRequest supplemented = new CredentialHeaders(() -> new BearerTokenAuthStrategy("fresh"))
                .withMissingAdded(anonymous().withAddedHeader("authorization", "Bearer captured"));

        assertThat(supplemented.headers()).filteredOn(header -> header.name().equalsIgnoreCase("Authorization"))
                .singleElement().satisfies(header -> assertThat(header.value()).isEqualTo("Bearer captured"));
    }

    private static HttpRequest anonymous() {
        return ImmutableHttpRequest.post(URL, Map.of("Content-Type", "application/json"), Seeds.TOOL_CALL);
    }
}
