package com.mcpscanner.access.mining;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.regex.Pattern;

final class IdShape {

    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern INTEGER = Pattern.compile("\\d{1,19}");
    private static final Pattern SLUG = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");

    private IdShape() {
    }

    static boolean idShaped(JsonNode value) {
        if (value.isIntegralNumber()) {
            return value.asLong() >= 0;
        }
        return value.isTextual() && idShaped(value.asText());
    }

    static boolean idShaped(String text) {
        return !routeShaped(text) && (UUID.matcher(text).matches() || INTEGER.matcher(text).matches()
                || SLUG.matcher(text).matches());
    }

    static boolean integral(JsonNode value) {
        return value.isIntegralNumber() || value.isTextual() && INTEGER.matcher(value.asText()).matches();
    }

    private static boolean routeShaped(String text) {
        return text.contains("://") || text.contains("/") || text.contains("\\") || text.contains("..")
                || text.startsWith("~");
    }
}
