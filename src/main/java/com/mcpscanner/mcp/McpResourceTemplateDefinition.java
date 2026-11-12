package com.mcpscanner.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record McpResourceTemplateDefinition(String uriTemplate, String name, String description,
                                            String mimeType, List<IconDescriptor> icons, JsonNode raw)
        implements CatalogItem {
    public McpResourceTemplateDefinition {
        icons = icons == null ? List.of() : List.copyOf(icons);
        raw = ReceivedJson.copyOf(raw);
    }

    public McpResourceTemplateDefinition(String uriTemplate, String name, String description, String mimeType) {
        this(uriTemplate, name, description, mimeType, List.of());
    }

    public McpResourceTemplateDefinition(String uriTemplate, String name, String description, String mimeType,
                                         List<IconDescriptor> icons) {
        this(uriTemplate, name, description, mimeType, icons, null);
    }

    @Override
    public List<String> problems() {
        return CatalogItem.requiring("uriTemplate", uriTemplate);
    }
}
