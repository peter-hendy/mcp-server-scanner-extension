package com.mcpscanner.testutil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;

public final class Json {

    private Json() {
    }

    public static JsonNode parse(String text) {
        try {
            return McpObjectMapper.INSTANCE.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
