package com.mcpscanner.ui.detail.schema;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

final class JsonText {

    private JsonText() {
    }

    static String compact(JsonNode value) {
        return value.toString();
    }

    static String plain(JsonNode value) {
        return value.isTextual() ? value.asText() : compact(value);
    }

    static List<JsonNode> elements(JsonNode array) {
        List<JsonNode> elements = new ArrayList<>();
        if (array.isArray()) {
            array.forEach(elements::add);
        }
        return elements;
    }
}
