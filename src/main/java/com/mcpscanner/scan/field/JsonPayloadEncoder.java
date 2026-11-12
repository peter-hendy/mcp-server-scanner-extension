package com.mcpscanner.scan.field;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.mcpscanner.mcp.JsonValueKind;
import com.mcpscanner.mcp.McpObjectMapper;

final class JsonPayloadEncoder {

    private static final ObjectReader STRICT_READER =
            McpObjectMapper.INSTANCE.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private JsonPayloadEncoder() {}

    static String encode(String payload, JsonValueKind kind) {
        return kind != JsonValueKind.STRING && parsesAs(payload, kind) ? payload : asJsonString(payload);
    }

    private static boolean parsesAs(String payload, JsonValueKind kind) {
        try {
            JsonNode node = STRICT_READER.readTree(payload);
            return node != null && switch (kind) {
                case NUMBER -> node.isNumber();
                case BOOLEAN -> node.isBoolean();
                case NULL -> node.isNull();
                case OBJECT -> node.isObject();
                case ARRAY -> node.isArray();
                case STRING -> false;
            };
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private static String asJsonString(String payload) {
        try {
            return McpObjectMapper.INSTANCE.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("String payload could not be JSON-encoded", e);
        }
    }
}
