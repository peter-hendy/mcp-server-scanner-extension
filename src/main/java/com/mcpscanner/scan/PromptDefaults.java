package com.mcpscanner.scan;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.mcp.McpPromptDefinition;

public final class PromptDefaults {

    private static final String PLACEHOLDER = "test";

    private PromptDefaults() {
    }

    public static ObjectNode arguments(McpPromptDefinition prompt) {
        ObjectNode arguments = McpObjectMapper.INSTANCE.createObjectNode();
        prompt.arguments().forEach(argument -> arguments.put(argument.name(), PLACEHOLDER));
        return arguments;
    }
}
