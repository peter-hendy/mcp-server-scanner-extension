package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ClientAddressVariantsTest {

    private final HttpRequest seed = Seeds.authenticated("https://api.example.com/mcp");

    @Test
    void claimsLoopbackAndControlsWithAnExternalAddressInEveryClientAddressHeader() {
        assertThat(new ClientAddressVariants().variantsFor(seed)).extracting(
                AuthVariant::name,
                variant -> variant.candidate().value(),
                variant -> variant.control().orElseThrow().value())
                .containsExactly(
                        tuple("forwarded-for", "127.0.0.1", "203.0.113.7"),
                        tuple("forwarded", "for=127.0.0.1", "for=203.0.113.7"),
                        tuple("client.x-real-ip", "127.0.0.1", "203.0.113.7"),
                        tuple("client.client-ip", "127.0.0.1", "203.0.113.7"));
    }

    @Test
    void keepsTheSeedCredentialsSoTheClaimIsTheOnlyChange() {
        AuthVariant forwardedFor = new ClientAddressVariants().variantsFor(seed).get(0);

        HttpRequest claimed = forwardedFor.candidate().applyTo(seed);

        assertThat(claimed.headerValue("X-Forwarded-For")).isEqualTo("127.0.0.1");
        assertThat(claimed.headerValue("Authorization")).isEqualTo("Bearer real-token");
        assertThat(claimed.headerValue("Mcp-Session-Id")).isEqualTo(Seeds.LIVE_SESSION);
        assertThat(claimed.bodyToString()).isEqualTo(seed.bodyToString());
    }
}
