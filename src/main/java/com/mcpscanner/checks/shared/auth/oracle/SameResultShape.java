package com.mcpscanner.checks.shared.auth.oracle;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.checks.shared.jsonrpc.JsonNodes;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class SameResultShape implements ResultEquivalence {

    @Override
    public boolean equivalent(JsonNode baselineResult, JsonNode candidateResult) {
        return shapeOf(baselineResult).equals(shapeOf(candidateResult));
    }

    private static String shapeOf(JsonNode node) {
        if (node.isObject()) {
            return objectShape(node);
        }
        if (node.isArray()) {
            return arrayShape(node);
        }
        return node.getNodeType().name();
    }

    private static String objectShape(JsonNode node) {
        Map<String, String> fields = new TreeMap<>();
        JsonNodes.fields(node).forEach(field -> fields.put(field.getKey(), shapeOf(field.getValue())));
        return fields.toString();
    }

    private static String arrayShape(JsonNode node) {
        Set<String> elements = new TreeSet<>();
        JsonNodes.elements(node).forEach(element -> elements.add(shapeOf(element)));
        return elements.toString();
    }
}
