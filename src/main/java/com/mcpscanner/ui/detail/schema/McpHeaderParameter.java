package com.mcpscanner.ui.detail.schema;

import com.fasterxml.jackson.databind.JsonNode;

final class McpHeaderParameter {

    static final String KEYWORD = "x-mcp-header";

    private static final String HEADER_PREFIX = "Mcp-Param-";

    private McpHeaderParameter() {
    }

    static boolean appliesAt(RowPosition position) {
        return position.depth() == 0;
    }

    static String headerOrNull(RowPosition position, JsonNode schema) {
        JsonNode annotation = schema.path(KEYWORD);
        return appliesAt(position) && annotation.isTextual() ? HEADER_PREFIX + annotation.asText() : null;
    }
}
