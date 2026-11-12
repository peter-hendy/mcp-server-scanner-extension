package com.mcpscanner.checks.catalog.jwtvalidation;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwkPemEncoderTest {

    private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();

    private final JwkPemEncoder encoder = new JwkPemEncoder();

    @Test
    void encodesAnRsaJwkAsSpkiAndPkcs1PemWithAndWithoutTheTrailingNewline() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        RSAPublicKey key = (RSAPublicKey) generator.generateKeyPair().getPublic();
        ObjectNode jwk = TestTokens.object("{\"kty\":\"RSA\",\"kid\":\"k1\"}")
                .put("n", URL.encodeToString(unsigned(key.getModulus())))
                .put("e", URL.encodeToString(unsigned(key.getPublicExponent())));

        List<PublicKeyMaterial> materials = encoder.encode(jwk, "https://as.example/jwks");

        String spki = pem("PUBLIC KEY", key.getEncoded());
        List<String> texts = materials.stream()
                .map(material -> new String(material.bytes(), StandardCharsets.US_ASCII)).toList();
        assertThat(texts).hasSize(4);
        assertThat(texts.get(0)).isEqualTo(spki);
        assertThat(texts.get(1)).isEqualTo(spki.stripTrailing());
        assertThat(texts.get(2)).startsWith("-----BEGIN RSA PUBLIC KEY-----\n").endsWith("-----END RSA PUBLIC KEY-----\n");
        assertThat(texts.get(3)).isEqualTo(texts.get(2).stripTrailing());
        assertThat(materials.get(0).description()).contains("k1", "https://as.example/jwks", "SPKI PEM");
        assertThat(materials.get(3).description()).contains("PKCS#1", "without the trailing newline");
    }

    @Test
    void pkcs1BodyIsTheDerSequenceOfModulusAndExponent() throws Exception {
        ObjectNode jwk = TestTokens.object("{\"kty\":\"RSA\"}")
                .put("n", URL.encodeToString(new byte[]{(byte) 0x80, 0x01}))
                .put("e", URL.encodeToString(new byte[]{0x01, 0x00, 0x01}));

        String pkcs1 = encoder.encode(jwk, "u").stream()
                .filter(material -> material.description().endsWith("PKCS#1 PEM"))
                .map(material -> new String(material.bytes(), StandardCharsets.US_ASCII))
                .findFirst().orElseThrow();
        String body = pkcs1.replace("-----BEGIN RSA PUBLIC KEY-----\n", "")
                .replace("\n-----END RSA PUBLIC KEY-----\n", "");

        assertThat(Base64.getDecoder().decode(body))
                .containsExactly(0x30, 0x0a, 0x02, 0x03, 0x00, 0x80, 0x01, 0x02, 0x03, 0x01, 0x00, 0x01);
    }

    @Test
    void encodesAnEcJwkAsSpkiPem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        ECPublicKey key = (ECPublicKey) generator.generateKeyPair().getPublic();
        ObjectNode jwk = TestTokens.object("{\"kty\":\"EC\",\"crv\":\"P-256\"}")
                .put("x", URL.encodeToString(fixed(key.getW().getAffineX(), 32)))
                .put("y", URL.encodeToString(fixed(key.getW().getAffineY(), 32)));

        List<PublicKeyMaterial> materials = encoder.encode(jwk, "u");

        assertThat(materials).extracting(material -> new String(material.bytes(), StandardCharsets.US_ASCII))
                .containsExactly(pem("PUBLIC KEY", key.getEncoded()), pem("PUBLIC KEY", key.getEncoded()).stripTrailing());
    }

    @Test
    void encodesAnEd25519JwkAsSpkiPem() {
        byte[] x = new byte[32];
        x[0] = 7;
        ObjectNode jwk = TestTokens.object("{\"kty\":\"OKP\",\"crv\":\"Ed25519\"}").put("x", URL.encodeToString(x));

        String pem = new String(encoder.encode(jwk, "u").get(0).bytes(), StandardCharsets.US_ASCII);

        byte[] der = Base64.getMimeDecoder().decode(pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", ""));
        assertThat(der).hasSize(44);
        assertThat(der[12]).isEqualTo((byte) 7);
    }

    @Test
    void ignoresSymmetricUnknownAndMalformedKeys() {
        assertThat(encoder.encode(TestTokens.object("{\"kty\":\"oct\",\"k\":\"c2VjcmV0\"}"), "u")).isEmpty();
        assertThat(encoder.encode(TestTokens.object("{\"kty\":\"RSA\",\"n\":\"%%\",\"e\":\"AQAB\"}"), "u")).isEmpty();
        assertThat(encoder.encode(TestTokens.object("{\"kty\":\"RSA\"}"), "u")).isEmpty();
        assertThat(encoder.encode(TestTokens.object("{\"kty\":\"EC\",\"crv\":\"P-999\",\"x\":\"AA\",\"y\":\"AA\"}"), "u"))
                .isEmpty();
        assertThat(encoder.encode(TestTokens.object("{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"AA\",\"y\":\"AA\"}"), "u"))
                .isEmpty();
    }

    private static String pem(String label, byte[] der) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
                + "\n-----END " + label + "-----\n";
    }

    private static byte[] unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        return bytes[0] == 0 ? java.util.Arrays.copyOfRange(bytes, 1, bytes.length) : bytes;
    }

    private static byte[] fixed(BigInteger value, int length) {
        byte[] bytes = unsigned(value);
        byte[] padded = new byte[length];
        System.arraycopy(bytes, 0, padded, length - bytes.length, bytes.length);
        return padded;
    }
}
