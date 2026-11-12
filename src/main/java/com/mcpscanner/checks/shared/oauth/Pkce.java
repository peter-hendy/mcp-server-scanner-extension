package com.mcpscanner.checks.shared.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public record Pkce(String verifier, String challenge) {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int VERIFIER_BYTES = 32;

    public static Pkce generate() {
        byte[] bytes = new byte[VERIFIER_BYTES];
        RANDOM.nextBytes(bytes);
        String verifier = base64Url(bytes);
        return new Pkce(verifier, base64Url(sha256(verifier)));
    }

    private static byte[] sha256(String verifier) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
