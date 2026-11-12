package com.mcpscanner.access;

import com.mcpscanner.mcp.McpToolDefinition;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class WriteReplayConfirmations {

    private final Set<String> confirmedTools = ConcurrentHashMap.newKeySet();

    public void confirm(Collection<McpToolDefinition> tools) {
        tools.forEach(tool -> confirmedTools.add(tool.name()));
    }

    public boolean confirmed(String toolName) {
        return confirmedTools.contains(toolName);
    }

    public void clear() {
        confirmedTools.clear();
    }
}
