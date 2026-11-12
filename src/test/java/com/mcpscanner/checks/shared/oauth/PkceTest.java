package com.mcpscanner.checks.shared.oauth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class PkceTest {

    @Test
    void theChallengeIsTheS256DigestOfTheVerifier() throws Exception {
        Pkce pkce = Pkce.generate();

        byte[] digest = MessageDigest.getInstance("SHA-256").digest(pkce.verifier().getBytes(StandardCharsets.US_ASCII));
        assertThat(pkce.challenge()).isEqualTo(Base64.getUrlEncoder().withoutPadding().encodeToString(digest));
    }

    @Test
    void theVerifierIsAnUnpaddedBase64UrlStringOfRfc7636Length() {
        assertThat(Pkce.generate().verifier()).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void everyPairIsFresh() {
        assertThat(Pkce.generate().verifier()).isNotEqualTo(Pkce.generate().verifier());
    }
}
