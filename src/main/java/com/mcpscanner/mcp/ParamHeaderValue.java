package com.mcpscanner.mcp;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class ParamHeaderValue {

    private static final String ENCODED_PREFIX = "=?base64?";
    private static final String ENCODED_SUFFIX = "?=";

    private ParamHeaderValue() {}

    public static String encoded(String value) {
        return travelsPlain(value) ? value : base64Wrapped(value);
    }

    private static boolean travelsPlain(String value) {
        return !value.isEmpty()
                && value.chars().allMatch(ParamHeaderValue::isPlainCharacter)
                && !isBlank(value.charAt(0))
                && !isBlank(value.charAt(value.length() - 1))
                && !looksEncoded(value);
    }

    private static boolean isPlainCharacter(int c) {
        return c == '\t' || (c >= 0x20 && c <= 0x7E);
    }

    private static boolean isBlank(char c) {
        return c == ' ' || c == '\t';
    }

    private static boolean looksEncoded(String value) {
        return value.startsWith(ENCODED_PREFIX) && value.endsWith(ENCODED_SUFFIX);
    }

    private static String base64Wrapped(String value) {
        return ENCODED_PREFIX + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8))
                + ENCODED_SUFFIX;
    }
}
