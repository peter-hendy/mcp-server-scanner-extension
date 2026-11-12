package com.mcpscanner.ui.detail;

import com.fasterxml.jackson.databind.JsonNode;

public final class McpAppsSection {

    public static final String TITLE = "MCP Apps";

    private static final String RESOURCE_LABEL = "UI resource";
    private static final String LEGACY_RESOURCE_URI = "ui/resourceUri";

    private McpAppsSection() {
    }

    public static FieldGrid grid(JsonNode meta) {
        return FieldGrid.withMonospacedLabels().addMonospaced(RESOURCE_LABEL, resourceUriOrNull(meta));
    }

    private static String resourceUriOrNull(JsonNode meta) {
        JsonNode nested = meta.path("ui").path("resourceUri");
        JsonNode uri = nested.isTextual() ? nested : meta.path(LEGACY_RESOURCE_URI);
        return uri.isTextual() ? uri.asText() : null;
    }
}
