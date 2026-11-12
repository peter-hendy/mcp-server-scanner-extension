package com.mcpscanner.cvetargets.manifest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Hs256JwtTokenTest {

    private static final Clock FIXED = Clock.fixed(Instant.ofEpochSecond(1_800_000_000L), ZoneOffset.UTC);

    @Test
    void signsTheClaimsWithIssuedAtAndExpiry() throws Exception {
        String token = new Hs256JwtToken("secret", Map.of("sub", "pair-validation"), 600).token(FIXED);
        String[] parts = token.split("\\.");

        JsonNode claims = new ObjectMapper().readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(claims.path("sub").asText()).isEqualTo("pair-validation");
        assertThat(claims.path("iat").asLong()).isEqualTo(1_800_000_000L);
        assertThat(claims.path("exp").asLong()).isEqualTo(1_800_000_600L);
        assertThat(parts[2]).isEqualTo(signature("secret", parts[0] + "." + parts[1]));
    }

    private static String signature(String secret, String signingInput) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
    }
}
