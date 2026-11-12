package com.mcpscanner.mcp;

import java.util.List;

public final class McpFieldNames {

    public static final String PREFIX = "MCP field: ";

    private McpFieldNames() {}

    public static String of(List<JsonStep> steps) {
        return PREFIX + JsonStep.pathOf(steps);
    }
}
