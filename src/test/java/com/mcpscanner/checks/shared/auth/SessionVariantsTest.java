package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionVariantsTest {

    @Test
    void sendsAnInvalidSessionIdWithANonexistentChildControl() {
        HttpRequest seed = Seeds.authenticated("https://api.example.com/mcp");
        AuthVariant variant = new SessionVariants(Seeds.CANARY).variantsFor(seed).get(0);

        assertThat(variant.name()).isEqualTo("session.invalid-id");
        assertThat(variant.family()).isEqualTo(VariantFamily.SESSION);
        HttpRequest candidate = variant.candidate().applyTo(seed);
        assertThat(candidate.headerValue("Mcp-Session-Id")).isEqualTo("invalid-c4n4ry");
        assertThat(candidate.path()).isEqualTo("/mcp");
        HttpRequest control = variant.control().orElseThrow().applyTo(seed);
        assertThat(control.headerValue("Mcp-Session-Id")).isEqualTo("invalid-c4n4ry");
        assertThat(control.path()).isEqualTo("/mcp/__mcp_c4n4ry");
    }
}
