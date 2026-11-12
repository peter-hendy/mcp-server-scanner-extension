package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.checks.shared.auth.AuthVariant;
import com.mcpscanner.checks.shared.auth.CredentialStripper;
import com.mcpscanner.checks.shared.auth.VariantFamily;
import com.mcpscanner.mcp.ScannerSentinels;
import com.mcpscanner.testutil.ImmutableHttpRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RetainedSessionVariantTest {

    private final RetainedSessionVariant variants =
            new RetainedSessionVariant(new CredentialStripper(() -> new BearerTokenAuthStrategy("t")));

    @Test
    void stripsCredentialsButKeepsTheSeedSession() {
        HttpRequest seed = ImmutableHttpRequest.post("http://mcp.example/mcp",
                Map.of("Authorization", "Bearer t", "Cookie", "c=1", "Mcp-Session-Id", "live"), "{}");

        AuthVariant variant = variants.variantFor(seed).orElseThrow();
        HttpRequest candidate = variant.candidate().applyTo(seed);

        assertThat(variant.family()).isEqualTo(VariantFamily.SESSION);
        assertThat(variant.control()).isEmpty();
        assertThat(RetainedSessionVariant.isRetainedSession(variant)).isTrue();
        assertThat(candidate.headerValue("Authorization")).isNull();
        assertThat(candidate.headerValue("Cookie")).isNull();
        assertThat(candidate.headerValue("Mcp-Session-Id")).isEqualTo("live");
        assertThat(candidate.headerValue(ScannerSentinels.KEEP_SESSION_HEADER)).isEqualTo(ScannerSentinels.SENTINEL_VALUE);
        assertThat(seed.headerValue("Authorization")).isEqualTo("Bearer t");
    }

    @Test
    void seedWithoutASessionHasNoRetainedSessionVariant() {
        assertThat(variants.variantFor(ImmutableHttpRequest.post("http://mcp.example/mcp", Map.of(), "{}"))).isEmpty();
    }
}
