package com.mcpscanner.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

final class HashedKey {

    private static final int HASH_HEX_CHARS = 16;
    private static final String NULL_SEGMENT = "null";

    private final String prefix;
    private final String value;
    private final String suffix;

    private HashedKey(String prefix, String value, String suffix) {
        this.prefix = prefix;
        this.value = value;
        this.suffix = suffix;
    }

    static HashedKey of(String prefix, String value, String suffix) {
        return new HashedKey(prefix, value, suffix);
    }

    String canonical() {
        return keyLowercasedWith(Locale.ROOT);
    }

    List<String> lookupOrder() {
        String canonical = canonical();
        String legacy = keyLowercasedWith(Locale.getDefault());
        return canonical.equals(legacy) ? List.of(canonical) : List.of(canonical, legacy);
    }

    private String keyLowercasedWith(Locale locale) {
        return prefix + segment(locale) + suffix;
    }

    private String segment(Locale locale) {
        return value == null ? NULL_SEGMENT : sha256Prefix(normalise(value, locale));
    }

    private static String normalise(String raw, Locale locale) {
        String normalised = raw.trim().toLowerCase(locale);
        while (normalised.endsWith("/")) {
            normalised = normalised.substring(0, normalised.length() - 1);
        }
        return normalised;
    }

    private static String sha256Prefix(String normalised) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(normalised.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, HASH_HEX_CHARS);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
