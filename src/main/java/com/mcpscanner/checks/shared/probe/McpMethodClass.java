package com.mcpscanner.checks.shared.probe;

import com.mcpscanner.mcp.McpMethods;

import java.util.Set;

public enum McpMethodClass {
    OPERATION,
    CATALOG,
    PROTOCOL,
    NOTIFICATION;

    private static final Set<String> OPERATIONS =
            Set.of(McpMethods.TOOLS_CALL, McpMethods.RESOURCES_READ, McpMethods.PROMPTS_GET);
    private static final Set<String> CATALOGS =
            Set.of(McpMethods.TOOLS_LIST, McpMethods.RESOURCES_LIST, McpMethods.RESOURCE_TEMPLATES_LIST,
                    McpMethods.PROMPTS_LIST);

    public static McpMethodClass of(String method, boolean notification) {
        if (notification) {
            return NOTIFICATION;
        }
        if (OPERATIONS.contains(method)) {
            return OPERATION;
        }
        return CATALOGS.contains(method) ? CATALOG : PROTOCOL;
    }
}
