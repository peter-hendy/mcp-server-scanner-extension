package com.mcpscanner.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record McpPromptDefinition(String name, String description, List<PromptArgument> arguments,
                                  List<IconDescriptor> icons, JsonNode raw)
        implements CatalogItem {

    public McpPromptDefinition {
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        icons = icons == null ? List.of() : List.copyOf(icons);
        raw = ReceivedJson.copyOf(raw);
    }

    public McpPromptDefinition(String name, String description, List<PromptArgument> arguments) {
        this(name, description, arguments, List.of());
    }

    public McpPromptDefinition(String name, String description, List<PromptArgument> arguments,
                               List<IconDescriptor> icons) {
        this(name, description, arguments, icons, null);
    }

    @Override
    public List<String> problems() {
        return CatalogItem.requiring("name", name);
    }
}
