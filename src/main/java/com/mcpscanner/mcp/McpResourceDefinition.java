package com.mcpscanner.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record McpResourceDefinition(String uri, String name, String description, String mimeType,
                                    List<IconDescriptor> icons, JsonNode raw) implements CatalogItem {

    public McpResourceDefinition {
        icons = icons == null ? List.of() : List.copyOf(icons);
        raw = ReceivedJson.copyOf(raw);
    }

    public McpResourceDefinition(String uri, String name, String description, String mimeType) {
        this(uri, name, description, mimeType, List.of());
    }

    public McpResourceDefinition(String uri, String name, String description, String mimeType,
                                 List<IconDescriptor> icons) {
        this(uri, name, description, mimeType, icons, null);
    }

    @Override
    public List<String> problems() {
        return CatalogItem.requiring("uri", uri);
    }
}
