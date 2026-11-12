package com.mcpscanner.checks.catalog.jwtvalidation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.mcpscanner.checks.catalog.jwtvalidation.TestTokens.token;
import static org.assertj.core.api.Assertions.assertThat;

class CompactJwtTest {

    @Test
    void parsesHeaderAndClaimsOfACompactToken() {
        CompactJwt jwt = CompactJwt.parse(token("{\"alg\":\"RS256\",\"kid\":\"k1\"}",
                "{\"iss\":\"https://as.example\",\"sub\":\"alice\"}")).orElseThrow();

        assertThat(jwt.algorithm()).isEqualTo("RS256");
        assertThat(jwt.header().path("kid").asText()).isEqualTo("k1");
        assertThat(jwt.issuer()).contains("https://as.example");
        assertThat(jwt.claims().path("sub").asText()).isEqualTo("alice");
    }

    @ParameterizedTest
    @ValueSource(strings = {"RS256", "RS512", "PS256", "ES256", "ES384", "EdDSA"})
    void recognisesAsymmetricAlgorithms(String algorithm) {
        assertThat(CompactJwt.parse(token("{\"alg\":\"" + algorithm + "\"}", "{}")).orElseThrow()
                .asymmetricallySigned()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"HS256", "none", ""})
    void symmetricOrMissingAlgorithmsAreNotAsymmetric(String algorithm) {
        assertThat(CompactJwt.parse(token("{\"alg\":\"" + algorithm + "\"}", "{}")).orElseThrow()
                .asymmetricallySigned()).isFalse();
    }

    @Test
    void issuerIsAbsentWhenNotAString() {
        assertThat(CompactJwt.parse(token("{\"alg\":\"RS256\"}", "{\"iss\":7}")).orElseThrow().issuer()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "a.b", "a.b.c.d", "not-a-jwt-at-all", "e30.W10.sig", "!!.e30.sig",
            "e30.bm90IGpzb24.sig"})
    void rejectsValuesThatAreNotCompactJwsWithObjectSegments(String value) {
        assertThat(CompactJwt.parse(value)).isEmpty();
    }

    @Test
    void rejectsNull() {
        assertThat(CompactJwt.parse(null)).isEmpty();
    }

    @Test
    void acceptsAnEmptySignatureSegment() {
        assertThat(CompactJwt.parse(JwtSegments.encode(TestTokens.object("{\"alg\":\"none\"}")) + "."
                + JwtSegments.encode(TestTokens.object("{}")) + ".")).isPresent();
    }
}
