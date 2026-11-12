package com.mcpscanner.client;

import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.mcp.McpResourceTemplateDefinition;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.ServerMetadata;

import java.util.List;

public record ConnectResult(
        ListOutcome<McpToolDefinition> toolsOutcome,
        ListOutcome<McpResourceDefinition> resourcesOutcome,
        ListOutcome<McpResourceTemplateDefinition> resourceTemplatesOutcome,
        ListOutcome<McpPromptDefinition> promptsOutcome,
        ServerMetadata serverMetadata) {

    public ConnectResult(List<McpToolDefinition> tools,
                         List<McpResourceDefinition> resources,
                         List<McpResourceTemplateDefinition> resourceTemplates,
                         List<McpPromptDefinition> prompts,
                         ServerMetadata serverMetadata) {
        this(ListOutcome.complete(tools), ListOutcome.complete(resources),
                ListOutcome.complete(resourceTemplates), ListOutcome.complete(prompts), serverMetadata);
    }

    public List<McpToolDefinition> tools() {
        return toolsOutcome.items();
    }

    public List<McpResourceDefinition> resources() {
        return resourcesOutcome.items();
    }

    public List<McpResourceTemplateDefinition> resourceTemplates() {
        return resourceTemplatesOutcome.items();
    }

    public List<McpPromptDefinition> prompts() {
        return promptsOutcome.items();
    }
}
