package com.mcpscanner.checks.api;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class CredentialHeaders {

    private CredentialHeaders() {
    }

    public static boolean same(Map<String, String> first, Map<String, String> second) {
        Map<String, String> normalised = normalised(first);
        return !normalised.isEmpty() && normalised.equals(normalised(second));
    }

    private static Map<String, String> normalised(Map<String, String> headers) {
        Map<String, String> normalised = new TreeMap<>();
        headers.forEach((name, value) -> normalised.put(name.toLowerCase(Locale.ROOT), value));
        return normalised;
    }
}
