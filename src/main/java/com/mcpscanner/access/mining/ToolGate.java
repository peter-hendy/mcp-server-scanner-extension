package com.mcpscanner.access.mining;

import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.ToolSafety;

import java.util.Set;

public final class ToolGate {

    private final Set<String> allowedTools;

    public ToolGate(Set<String> allowedTools) {
        this.allowedTools = Set.copyOf(allowedTools);
    }

    public boolean mayCall(McpToolDefinition tool) {
        return ToolSafety.minable(tool, allowedTools);
    }

    public static boolean takesMinedValues(McpToolDefinition tool) {
        return !ToolSafety.needsOptIn(tool);
    }
}
