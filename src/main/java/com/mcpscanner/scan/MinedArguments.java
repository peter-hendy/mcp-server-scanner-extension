package com.mcpscanner.scan;

import com.mcpscanner.mcp.McpToolDefinition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public record MinedArguments(Map<String, Set<String>> byTool) {

    public static final MinedArguments NONE = new MinedArguments(Map.of());

    public MinedArguments {
        Map<String, Set<String>> kept = new HashMap<>();
        byTool.forEach((tool, arguments) -> {
            if (!arguments.isEmpty()) {
                kept.put(tool, Set.copyOf(arguments));
            }
        });
        byTool = Map.copyOf(kept);
    }

    public static MinedArguments of(List<McpToolDefinition> tools, Function<McpToolDefinition, Set<String>> mined) {
        Map<String, Set<String>> byTool = new HashMap<>();
        tools.forEach(tool -> byTool.put(tool.name(), mined.apply(tool)));
        return new MinedArguments(byTool);
    }

    public Set<String> of(McpToolDefinition tool) {
        return byTool.getOrDefault(tool.name(), Set.of());
    }

    public boolean presentIn(List<McpToolDefinition> tools) {
        return tools.stream().anyMatch(tool -> !of(tool).isEmpty());
    }

    public boolean present() {
        return !byTool.isEmpty();
    }
}
