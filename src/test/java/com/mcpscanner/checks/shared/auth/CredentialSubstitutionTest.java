package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.CustomHeaderAuthStrategy;
import com.mcpscanner.mcp.ScannerSentinels;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialSubstitutionTest {

    private static final String URL = "http://127.0.0.1:8000/mcp";
    private final CredentialSubstitution substitution =
            new CredentialSubstitution(() -> new CustomHeaderAuthStrategy(Map.of("X-Api-Key", "real-key")));

    @Test
    void anAnonymousRequestCarriesNoCredentialButKeepsTheSession() {
        HttpRequest anonymous = substitution.anonymous(Seeds.authenticated(URL));

        assertThat(anonymous.hasHeader("Authorization")).isFalse();
        assertThat(anonymous.hasHeader("Cookie")).isFalse();
        assertThat(anonymous.hasHeader("X-Api-Key")).isFalse();
        assertThat(anonymous.headerValue(ScannerSentinels.STRIP_AUTH_HEADER)).isEqualTo(ScannerSentinels.SENTINEL_VALUE);
        assertThat(anonymous.headerValue("Mcp-Session-Id")).isEqualTo(Seeds.LIVE_SESSION);
    }

    @Test
    void aBearerRequestCarriesOnlyTheGivenToken() {
        HttpRequest bearer = substitution.bearer(Seeds.authenticated(URL), "probe-token");

        assertThat(bearer.headerValue("Authorization")).isEqualTo("Bearer probe-token");
        assertThat(bearer.hasHeader("Cookie")).isFalse();
        assertThat(bearer.hasHeader("X-Api-Key")).isFalse();
        assertThat(bearer.headerValue(ScannerSentinels.STRIP_AUTH_HEADER)).isEqualTo(ScannerSentinels.SENTINEL_VALUE);
        assertThat(bearer.headerValue("Mcp-Session-Id")).isEqualTo(Seeds.LIVE_SESSION);
    }

    @Test
    void replacingTheIdentityAppliesTheGivenHeadersInPlaceOfEveryCredential() {
        HttpRequest replaced = substitution.withIdentity(Seeds.authenticated(URL), Map.of("X-Api-Key", "live-key"));

        assertThat(replaced.headerValue("X-Api-Key")).isEqualTo("live-key");
        assertThat(replaced.hasHeader("Authorization")).isFalse();
        assertThat(replaced.hasHeader(ScannerSentinels.STRIP_AUTH_HEADER)).isFalse();
    }
}
