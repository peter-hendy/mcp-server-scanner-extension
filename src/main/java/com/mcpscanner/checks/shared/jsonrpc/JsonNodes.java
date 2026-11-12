package com.mcpscanner.checks.shared.jsonrpc;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class JsonNodes {

    private JsonNodes() {}

    public static List<JsonNode> elements(JsonNode array) {
        List<JsonNode> elements = new ArrayList<>();
        array.elements().forEachRemaining(elements::add);
        return elements;
    }

    public static List<Map.Entry<String, JsonNode>> fields(JsonNode object) {
        List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
        object.fields().forEachRemaining(fields::add);
        return fields;
    }
}
