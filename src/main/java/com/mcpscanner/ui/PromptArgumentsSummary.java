package com.mcpscanner.ui;

import com.mcpscanner.ui.widgets.Plurals;

import java.util.List;

final class PromptArgumentsSummary {

    private static final String SEPARATOR = " · ";
    private static final String VALUE_TYPE = "all values are strings";

    private PromptArgumentsSummary() {
    }

    static String of(List<PromptArgumentEntry> entries) {
        long required = entries.stream().filter(entry -> entry.argument().required()).count();
        return Plurals.count(entries.size(), "argument") + SEPARATOR + required + " required" + SEPARATOR + VALUE_TYPE;
    }
}
