package com.mcpscanner.ui.detail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class JsonValueText {

    private static final String SEPARATOR = ", ";

    private JsonValueText() {
    }

    public static String of(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return "";
        }
        if (node.isArray()) {
            return array(node);
        }
        if (node.isObject()) {
            return object(node);
        }
        return node.toString();
    }

    private static String array(JsonNode node) {
        return StreamSupport.stream(node.spliterator(), false)
                .map(JsonValueText::of)
                .collect(Collectors.joining(SEPARATOR, "[", "]"));
    }

    private static String object(JsonNode node) {
        return node.properties().stream()
                .map(JsonValueText::member)
                .collect(Collectors.joining(SEPARATOR, "{", "}"));
    }

    private static String member(Map.Entry<String, JsonNode> entry) {
        return TextNode.valueOf(entry.getKey()) + ": " + of(entry.getValue());
    }
}
