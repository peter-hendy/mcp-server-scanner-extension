package com.mcpscanner.checks.catalog.jwtvalidation;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimMutationTest {

    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(1_800_000_000L), ZoneOffset.UTC);
    private static final ObjectNode CLAIMS = TestTokens.object(
            "{\"iss\":\"https://as.example\",\"aud\":[\"https://mcp.example/mcp\"],\"sub\":\"alice\","
                    + "\"exp\":4102444800,\"nbf\":1,\"iat\":1}");

    @Test
    void wrongIssuerReplacesOnlyTheIssuer() {
        ObjectNode mutated = ClaimMutation.WRONG_ISSUER.applyTo(CLAIMS, CLOCK);

        assertThat(mutated.path("iss").asText()).isEqualTo("https://untrusted-issuer.mcp-scanner.invalid");
        assertThat((Object) mutated.without("iss")).isEqualTo(CLAIMS.deepCopy().without("iss"));
    }

    @Test
    void wrongAudienceReplacesOnlyTheAudience() {
        ObjectNode mutated = ClaimMutation.WRONG_AUDIENCE.applyTo(CLAIMS, CLOCK);

        assertThat(mutated.path("aud").asText()).isEqualTo("https://other-resource.mcp-scanner.invalid/mcp");
        assertThat((Object) mutated.without("aud")).isEqualTo(CLAIMS.deepCopy().without("aud"));
    }

    @Test
    void expiredMovesExpiryAndIssueTimeIntoThePast() {
        ObjectNode mutated = ClaimMutation.EXPIRED.applyTo(CLAIMS, CLOCK);

        assertThat(mutated.path("exp").asLong()).isEqualTo(1_800_000_000L - 3600);
        assertThat(mutated.path("iat").asLong()).isEqualTo(1_800_000_000L - 7200);
        assertThat(mutated.path("nbf").asLong()).isEqualTo(1_800_000_000L - 7200);
        assertThat(mutated.path("sub").asText()).isEqualTo("alice");
    }

    @Test
    void expiredLeavesAbsentNotBeforeAbsent() {
        ObjectNode mutated = ClaimMutation.EXPIRED.applyTo(TestTokens.object("{\"exp\":1}"), CLOCK);

        assertThat(mutated.has("nbf")).isFalse();
    }

    @Test
    void mutationsNeverChangeTheCapturedClaims() {
        ObjectNode before = CLAIMS.deepCopy();

        for (ClaimMutation mutation : ClaimMutation.values()) {
            mutation.applyTo(CLAIMS, CLOCK);
        }

        assertThat(CLAIMS).isEqualTo(before);
    }

    @Test
    void everyMutationNamesTheValidationItBypasses() {
        assertThat(ClaimMutation.WRONG_ISSUER.unvalidatedClaim()).isEqualTo("issuer (iss)");
        assertThat(ClaimMutation.WRONG_AUDIENCE.unvalidatedClaim()).isEqualTo("audience (aud)");
        assertThat(ClaimMutation.EXPIRED.unvalidatedClaim()).isEqualTo("expiry (exp)");
    }
}
