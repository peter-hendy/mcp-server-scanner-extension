package com.mcpscanner.mcp;

import com.fasterxml.jackson.databind.JsonNode;

final class ReceivedJson {

    private ReceivedJson() {
    }

    static JsonNode copyOf(JsonNode raw) {
        return raw == null ? McpObjectMapper.INSTANCE.createObjectNode() : raw.deepCopy();
    }
}
