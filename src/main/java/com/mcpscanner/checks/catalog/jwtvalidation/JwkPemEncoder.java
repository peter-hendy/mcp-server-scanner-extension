package com.mcpscanner.checks.catalog.jwtvalidation;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class JwkPemEncoder {

    private static final String SPKI_LABEL = "PUBLIC KEY";
    private static final String PKCS1_LABEL = "RSA PUBLIC KEY";
    private static final Map<String, EcCurve> EC_CURVES = Map.of(
            "P-256", new EcCurve("secp256r1", 32), "P-384", new EcCurve("secp384r1", 48),
            "P-521", new EcCurve("secp521r1", 66));
    private static final byte[] ED25519_SPKI_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");
    private static final int ED25519_KEY_BYTES = 32;
    private static final Base64.Encoder PEM_BODY = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII));
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    List<PublicKeyMaterial> encode(JsonNode jwk, String source) {
        String keyName = keyName(jwk, source);
        List<PublicKeyMaterial> materials = new ArrayList<>();
        spki(jwk).ifPresent(der -> addPem(materials, keyName, "SPKI PEM", SPKI_LABEL, der));
        pkcs1(jwk).ifPresent(der -> addPem(materials, keyName, "PKCS#1 PEM", PKCS1_LABEL, der));
        return materials;
    }

    private static String keyName(JsonNode jwk, String source) {
        String keyId = jwk.path("kid").asText("");
        return "the public key " + (keyId.isEmpty() ? "" : keyId + " ") + "from " + source;
    }

    private static void addPem(List<PublicKeyMaterial> materials, String keyName, String format, String label,
                               byte[] der) {
        String pem = "-----BEGIN " + label + "-----\n" + PEM_BODY.encodeToString(der) + "\n-----END " + label + "-----\n";
        materials.add(new PublicKeyMaterial(keyName + " as " + format,
                pem.getBytes(StandardCharsets.US_ASCII)));
        materials.add(new PublicKeyMaterial(keyName + " as " + format + " without the trailing newline",
                pem.stripTrailing().getBytes(StandardCharsets.US_ASCII)));
    }

    private static Optional<byte[]> spki(JsonNode jwk) {
        try {
            return switch (jwk.path("kty").asText()) {
                case "RSA" -> Optional.of(KeyFactory.getInstance("RSA")
                        .generatePublic(new RSAPublicKeySpec(integer(jwk, "n"), integer(jwk, "e"))).getEncoded());
                case "EC" -> ecSpki(jwk);
                case "OKP" -> ed25519Spki(jwk);
                default -> Optional.empty();
            };
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Optional<byte[]> ecSpki(JsonNode jwk) throws GeneralSecurityException {
        EcCurve curve = EC_CURVES.get(jwk.path("crv").asText());
        if (curve == null || bytes(jwk, "x").length != curve.coordinateBytes()
                || bytes(jwk, "y").length != curve.coordinateBytes()) {
            return Optional.empty();
        }
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec(curve.standardName()));
        ECPoint point = new ECPoint(integer(jwk, "x"), integer(jwk, "y"));
        return Optional.of(KeyFactory.getInstance("EC").generatePublic(
                new ECPublicKeySpec(point, parameters.getParameterSpec(ECParameterSpec.class))).getEncoded());
    }

    private static Optional<byte[]> ed25519Spki(JsonNode jwk) {
        byte[] x = bytes(jwk, "x");
        if (!"Ed25519".equals(jwk.path("crv").asText()) || x.length != ED25519_KEY_BYTES) {
            return Optional.empty();
        }
        ByteArrayOutputStream der = new ByteArrayOutputStream();
        der.writeBytes(ED25519_SPKI_PREFIX);
        der.writeBytes(x);
        return Optional.of(der.toByteArray());
    }

    private static Optional<byte[]> pkcs1(JsonNode jwk) {
        if (!"RSA".equals(jwk.path("kty").asText())) {
            return Optional.empty();
        }
        try {
            return Optional.of(DerSequence.of(integer(jwk, "n"), integer(jwk, "e")));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static BigInteger integer(JsonNode jwk, String member) {
        return new BigInteger(1, bytes(jwk, member));
    }

    private static byte[] bytes(JsonNode jwk, String member) {
        JsonNode value = jwk.path(member);
        if (!value.isTextual() || value.asText().isEmpty()) {
            throw new IllegalArgumentException("JWK member " + member + " is missing");
        }
        return URL_DECODER.decode(value.asText());
    }

    private record EcCurve(String standardName, int coordinateBytes) {
    }
}
