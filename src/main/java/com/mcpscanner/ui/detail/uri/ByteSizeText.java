package com.mcpscanner.ui.detail.uri;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.ui.detail.JsonValueText;

import java.util.List;
import java.util.Locale;

public final class ByteSizeText {

    private static final List<String> UNITS = List.of("KB", "MB", "GB", "TB", "PB");
    private static final double STEP = 1024;

    private ByteSizeText() {
    }

    public static String of(JsonNode size) {
        if (!isWholeLong(size) || size.asLong() < 0) {
            return JsonValueText.of(size);
        }
        long bytes = size.asLong();
        return bytes < STEP ? exact(bytes) : scaled(bytes) + " (" + exact(bytes) + ")";
    }

    private static boolean isWholeLong(JsonNode size) {
        return size.canConvertToExactIntegral() && size.canConvertToLong();
    }

    private static String scaled(long bytes) {
        double value = bytes / STEP;
        int unit = 0;
        while (value >= STEP && unit < UNITS.size() - 1) {
            value /= STEP;
            unit++;
        }
        return String.format(Locale.ENGLISH, "%.1f %s", value, UNITS.get(unit));
    }

    private static String exact(long bytes) {
        return String.format(Locale.ENGLISH, "%,d %s", bytes, bytes == 1 ? "byte" : "bytes");
    }
}
