package com.mcpscanner.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record McpToolDefinition(
        String name,
        String description,
        String inputSchema,
        List<IconDescriptor> icons,
        ToolAnnotations annotations,
        JsonNode raw) implements CatalogItem {

    public McpToolDefinition {
        icons = icons == null ? List.of() : List.copyOf(icons);
        annotations = annotations == null ? ToolAnnotations.EMPTY : annotations;
        raw = ReceivedJson.copyOf(raw);
    }

    public McpToolDefinition(String name, String description, String inputSchema) {
        this(name, description, inputSchema, List.of(), ToolAnnotations.EMPTY);
    }

    public McpToolDefinition(String name, String description, String inputSchema, List<IconDescriptor> icons) {
        this(name, description, inputSchema, icons, ToolAnnotations.EMPTY);
    }

    public McpToolDefinition(String name, String description, String inputSchema, List<IconDescriptor> icons,
                             ToolAnnotations annotations) {
        this(name, description, inputSchema, icons, annotations, null);
    }

    @Override
    public List<String> problems() {
        return CatalogItem.requiring("name", name);
    }
}
