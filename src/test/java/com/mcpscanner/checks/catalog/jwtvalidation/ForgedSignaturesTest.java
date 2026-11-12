package com.mcpscanner.checks.catalog.jwtvalidation;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class ForgedSignaturesTest {

    private static final ObjectNode CLAIMS = TestTokens.object("{\"sub\":\"alice\",\"exp\":4102444800}");
    private static final CompactJwt CAPTURED = CompactJwt.parse(TestTokens.RS256_TOKEN).orElseThrow();

    @Test
    void hmacMatchesTheRfc4231StyleVectorForAnEmptyKeyAndMessage() {
        assertThat(HexFormat.of().formatHex(HmacSha256.digest(new byte[0], new byte[0])))
                .isEqualTo("b613679a0814d9ec772f95d778c35fc5ff1697c493715653c6c712144292c5ad");
    }

    @Test
    void hmacMatchesAKnownVectorForANonEmptyKey() {
        assertThat(HexFormat.of().formatHex(HmacSha256.digest("key".getBytes(StandardCharsets.UTF_8),
                "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8))))
                .isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
    }

    @Test
    void hmacSignatureSignsTheEncodedHeaderAndClaimsWithItsKey() {
        byte[] key = "secret".getBytes(StandardCharsets.UTF_8);
        String token = new HmacSignature("hmac", ForgedHeader.from(CAPTURED, "HS256"), key).sign(CLAIMS);

        assertThat(TestTokens.headerOf(token).path("alg").asText()).isEqualTo("HS256");
        assertThat(TestTokens.claimsOf(token)).isEqualTo(CLAIMS);
        assertThat(TestTokens.signatureOf(token)).isEqualTo(JwtSegments.encode(
                HmacSha256.digest(key, TestTokens.signingInputOf(token).getBytes(StandardCharsets.US_ASCII))));
    }

    @Test
    void algNoneHasAnEmptySignatureAndKeepsTheClaims() {
        String token = new UnsignedSignature(CAPTURED).sign(CLAIMS);

        assertThat(TestTokens.headerOf(token).path("alg").asText()).isEqualTo("none");
        assertThat(TestTokens.signatureOf(token)).isEmpty();
        assertThat(TestTokens.claimsOf(token)).isEqualTo(CLAIMS);
    }

    @Test
    void randomRsaSignatureIsAValidRs256SignatureFromAKeyTheServerNeverIssued() throws GeneralSecurityException {
        RandomRsaSignature signature = new RandomRsaSignature(CAPTURED);
        String token = signature.sign(CLAIMS);

        java.security.Signature verifier = java.security.Signature.getInstance("SHA256withRSA");
        verifier.initVerify(signature.publicKey());
        verifier.update(TestTokens.signingInputOf(token).getBytes(StandardCharsets.US_ASCII));

        assertThat(verifier.verify(java.util.Base64.getUrlDecoder().decode(TestTokens.signatureOf(token)))).isTrue();
        assertThat(TestTokens.headerOf(token).path("alg").asText()).isEqualTo("RS256");
    }

    @Test
    void twoRandomRsaSignaturesUseDifferentKeys() {
        assertThat(new RandomRsaSignature(CAPTURED).publicKey())
                .isNotEqualTo(new RandomRsaSignature(CAPTURED).publicKey());
    }

    @Test
    void forgedHeadersKeepOnlyStringKeySelectorsFromTheCapturedToken() {
        CompactJwt captured = CompactJwt.parse(TestTokens.token(
                "{\"alg\":\"RS256\",\"typ\":\"at+jwt\",\"kid\":\"k1\",\"x5t\":\"t1\",\"x5t#S256\":\"t2\","
                        + "\"jku\":\"https://evil.example/jwks\",\"jwk\":{},\"cty\":\"x\"}", "{}")).orElseThrow();

        ObjectNode header = ForgedHeader.from(captured, "HS256");

        assertThat(header).isEqualTo(TestTokens.object(
                "{\"alg\":\"HS256\",\"typ\":\"at+jwt\",\"kid\":\"k1\",\"x5t\":\"t1\",\"x5t#S256\":\"t2\"}"));
    }

    @Test
    void forgedHeadersDefaultTheTypeAndSkipNonStringSelectors() {
        CompactJwt captured = CompactJwt.parse(TestTokens.token("{\"alg\":\"RS256\",\"kid\":5}", "{}")).orElseThrow();

        assertThat(ForgedHeader.from(captured, "none")).isEqualTo(TestTokens.object("{\"alg\":\"none\",\"typ\":\"JWT\"}"));
    }

    @Test
    void unknownKeyIdHeaderNamesOnlyTheAlgorithmTypeAndKeyId() {
        assertThat(ForgedHeader.withKeyId("HS256", "unknown-1"))
                .isEqualTo(TestTokens.object("{\"alg\":\"HS256\",\"typ\":\"JWT\",\"kid\":\"unknown-1\"}"));
    }
}
