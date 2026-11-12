package com.mcpscanner.scan;

import com.mcpscanner.mcp.McpToolDefinition;

import java.util.List;
import java.util.Map;
import java.util.Set;

@FunctionalInterface
public interface OwnerArgumentSource {

    OwnerArgumentSource NONE = tool -> Map.of();

    Map<String, String> forTool(McpToolDefinition tool);

    default Set<String> minedArgumentsFor(McpToolDefinition tool) {
        return Set.of();
    }

    default MinedArguments minedArguments(List<McpToolDefinition> tools) {
        return MinedArguments.of(tools, this::minedArgumentsFor);
    }
}
