package com.mcpscanner.checks.shared.oauth;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class OAuthResponseParameters {

    private static final int MALFORMED = Integer.MAX_VALUE;

    private final String rawParameters;

    public OAuthResponseParameters(String rawParameters) {
        this.rawParameters = rawParameters;
    }

    public int count(String name) {
        int occurrences = 0;
        try {
            for (String parameter : rawParameters.split("&", -1)) {
                if (name.equals(decode(parameter.split("=", 2)[0]))) {
                    occurrences++;
                }
            }
        } catch (IllegalArgumentException malformed) {
            return MALFORMED;
        }
        return occurrences;
    }

    public Optional<String> single(String name) {
        String found = null;
        try {
            for (String parameter : rawParameters.split("&", -1)) {
                String[] parts = parameter.split("=", 2);
                if (!name.equals(decode(parts[0]))) {
                    continue;
                }
                if (found != null || parts.length != 2) {
                    return Optional.empty();
                }
                found = decode(parts[1]);
            }
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
        return found == null || found.isBlank() ? Optional.empty() : Optional.of(found);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
