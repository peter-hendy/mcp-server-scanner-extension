package com.mcpscanner.ui.detail.uri;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.ui.detail.JsonValueText;

public final class PriorityText {

    private static final double LOWEST = 0;
    private static final double HIGHEST = 1;

    private PriorityText() {
    }

    public static String of(JsonNode priority) {
        if (!priority.isNumber()) {
            return JsonValueText.of(priority);
        }
        double value = priority.asDouble();
        boolean inRange = value >= LOWEST && value <= HIGHEST;
        return priority.asText() + (inRange ? " of 1.0" : " (outside 0 to 1)");
    }
}
