package com.mcpscanner.scan;

import com.mcpscanner.mcp.CatalogItem;
import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.mcp.McpResourceTemplateDefinition;
import com.mcpscanner.mcp.McpToolDefinition;

import java.util.List;

public record ScanInventory(
        List<McpToolDefinition> tools,
        List<McpResourceDefinition> resources,
        List<McpResourceTemplateDefinition> resourceTemplates,
        List<McpPromptDefinition> prompts) {

    public ScanInventory {
        tools = CatalogItem.wellFormed(tools);
        resources = CatalogItem.wellFormed(resources);
        resourceTemplates = CatalogItem.wellFormed(resourceTemplates);
        prompts = CatalogItem.wellFormed(prompts);
    }

    public static ScanInventory toolsOnly(List<McpToolDefinition> tools) {
        return new ScanInventory(tools, List.of(), List.of(), List.of());
    }

    public static ScanInventory empty() {
        return new ScanInventory(List.of(), List.of(), List.of(), List.of());
    }

    public int totalCount() {
        return tools.size() + resources.size() + resourceTemplates.size() + prompts.size();
    }
}
