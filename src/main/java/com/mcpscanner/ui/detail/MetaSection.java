package com.mcpscanner.ui.detail;

import com.fasterxml.jackson.databind.JsonNode;

public final class MetaSection {

    public static final String TITLE = "Metadata (_meta)";

    private MetaSection() {
    }

    public static DetailSection of(JsonNode meta) {
        return DetailSection.of(TITLE, grid(meta));
    }

    public static FieldGrid grid(JsonNode meta) {
        FieldGrid grid = FieldGrid.withMonospacedLabels();
        if (meta != null && meta.isObject()) {
            meta.properties().forEach(entry -> grid.addMonospaced(entry.getKey(), JsonValueText.of(entry.getValue())));
        }
        return grid;
    }
}
