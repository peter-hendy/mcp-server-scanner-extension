package com.mcpscanner.ui.detail.schema;

import com.mcpscanner.ui.widgets.Plurals;

public record SchemaSummary(int parameterCount, int requiredCount, String extraPropertiesRule) {

    private static final String SEPARATOR = " · ";

    public String text() {
        return Plurals.count(parameterCount, "parameter") + SEPARATOR + requiredCount + " required" + SEPARATOR
                + extraPropertiesRule;
    }
}
