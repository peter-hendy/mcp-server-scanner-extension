package com.mcpscanner.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

public final class JsonLeaves {

    private JsonLeaves() {
    }

    public static List<Leaf> under(JsonNode node, List<Object> prefix) {
        List<Leaf> leaves = new ArrayList<>();
        collect(node, new ArrayList<>(prefix), leaves);
        return List.copyOf(leaves);
    }

    public static void replace(ObjectNode params, Leaf leaf, JsonNode value) {
        JsonNode parent = params;
        List<Object> path = leaf.path();
        for (Object step : path.subList(0, path.size() - 1)) {
            parent = step instanceof Integer index ? parent.path(index) : parent.path((String) step);
        }
        Object last = path.get(path.size() - 1);
        if (parent instanceof ObjectNode object && last instanceof String name) {
            object.set(name, value);
        } else if (parent instanceof ArrayNode array && last instanceof Integer index) {
            array.set(index, value);
        } else {
            throw new IllegalArgumentException("unsupported argument path " + path);
        }
    }

    private static void collect(JsonNode value, List<Object> path, List<Leaf> leaves) {
        if (value.isTextual() || value.isNumber()) {
            leaves.add(new Leaf(path, value));
        } else if (value.isObject()) {
            value.fields().forEachRemaining(entry -> {
                path.add(entry.getKey());
                collect(entry.getValue(), path, leaves);
                path.remove(path.size() - 1);
            });
        } else if (value.isArray()) {
            for (int index = 0; index < value.size(); index++) {
                path.add(index);
                collect(value.get(index), path, leaves);
                path.remove(path.size() - 1);
            }
        }
    }
}
