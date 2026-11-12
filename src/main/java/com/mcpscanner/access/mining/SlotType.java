package com.mcpscanner.access.mining;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BigIntegerNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.math.BigInteger;
import java.util.Set;

enum SlotType {
    STRING,
    INTEGER,
    ANY;

    private static final Set<String> NUMERIC = Set.of("integer", "number");

    static SlotType of(JsonNode propertySchema) {
        String type = propertySchema.path("type").asText();
        if (NUMERIC.contains(type)) {
            return INTEGER;
        }
        return "string".equals(type) ? STRING : ANY;
    }

    boolean accepts(JsonNode value) {
        return this == INTEGER ? IdShape.integral(value) : value.isTextual() || value.isIntegralNumber();
    }

    JsonNode render(String value) {
        return this == INTEGER ? BigIntegerNode.valueOf(new BigInteger(value)) : TextNode.valueOf(value);
    }
}
