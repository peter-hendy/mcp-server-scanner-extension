package com.mcpscanner.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public record ServerMetadata(Map<String, String> serverInfo,
                             String instructions,
                             Map<String, Object> capabilities,
                             List<IconDescriptor> icons,
                             JsonNode raw) {

    public ServerMetadata {
        icons = icons == null ? List.of() : List.copyOf(icons);
        raw = ReceivedJson.copyOf(raw);
    }

    public ServerMetadata(Map<String, String> serverInfo, String instructions, Map<String, Object> capabilities) {
        this(serverInfo, instructions, capabilities, List.of());
    }

    public ServerMetadata(Map<String, String> serverInfo, String instructions, Map<String, Object> capabilities,
                          List<IconDescriptor> icons) {
        this(serverInfo, instructions, capabilities, icons, null);
    }

    public static ServerMetadata empty() {
        return new ServerMetadata(Map.of(), "", Map.of(), List.of());
    }
}
