package com.mcpscanner.checks.api.crossidentity;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.mcpscanner.checks.shared.jsonrpc.JsonNodes;
import com.mcpscanner.mcp.McpObjectMapper;

import java.io.IOException;
import java.util.List;

public final class EmptyContent {

    private static final List<String> CONTENT_FIELDS = List.of("content", "contents");
    private static final List<String> BODY_FIELDS = List.of("text", "blob", "data");
    private static final ObjectReader STRICT_JSON = McpObjectMapper.INSTANCE.reader()
            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private EmptyContent() {
    }

    public static boolean empty(JsonNode payload) {
        if (payload == null || !emptyValue(payload.path("structuredContent"))) {
            return payload == null;
        }
        for (String field : CONTENT_FIELDS) {
            JsonNode items = payload.path(field);
            if (items.isArray() && !items.isEmpty()) {
                return JsonNodes.elements(items).stream().allMatch(EmptyContent::emptyItem);
            }
        }
        return true;
    }

    private static boolean emptyItem(JsonNode item) {
        if (!item.isObject()) {
            return true;
        }
        if (item.path("resource").isObject()) {
            return emptyItem(item.path("resource"));
        }
        JsonNode body = BODY_FIELDS.stream().map(item::path).filter(node -> !node.isMissingNode()).findFirst()
                .orElse(item.path("text"));
        return body.isTextual() ? emptyText(body.asText().strip()) : emptyValue(body);
    }

    private static boolean emptyText(String text) {
        try {
            return emptyValue(STRICT_JSON.readTree(text));
        } catch (IOException notJson) {
            return text.isEmpty();
        }
    }

    private static boolean emptyValue(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return true;
        }
        if (value.isTextual()) {
            return value.asText().isBlank();
        }
        if (value.isArray()) {
            return value.isEmpty();
        }
        if (value.isObject()) {
            for (JsonNode child : value) {
                if (!emptyValue(child)) {
                    return false;
                }
            }
            return true;
        }
        if (value.isNumber()) {
            return value.decimalValue().signum() == 0;
        }
        return value.isBoolean() && !value.booleanValue();
    }
}
