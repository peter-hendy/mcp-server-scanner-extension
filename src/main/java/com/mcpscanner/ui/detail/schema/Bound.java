package com.mcpscanner.ui.detail.schema;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

record Bound(String value, boolean exclusive) {

    static Bound inclusive(JsonNode value) {
        return value.isNumber() ? new Bound(value.asText(), false) : null;
    }

    static Bound lower(JsonNode schema) {
        return limit(schema, "minimum", "exclusiveMinimum");
    }

    static Bound upper(JsonNode schema) {
        return limit(schema, "maximum", "exclusiveMaximum");
    }

    static Optional<String> describe(Bound lower, Bound upper) {
        if (lower != null && upper != null && !lower.exclusive && !upper.exclusive) {
            return Optional.of(lower.value + "–" + upper.value);
        }
        String text = Stream.of(lower == null ? null : lower.text(">", "≥"), upper == null ? null : upper.text("<", "≤"))
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    private static Bound limit(JsonNode schema, String inclusiveKey, String exclusiveKey) {
        JsonNode exclusive = schema.path(exclusiveKey);
        if (exclusive.isNumber()) {
            return new Bound(exclusive.asText(), true);
        }
        JsonNode inclusive = schema.path(inclusiveKey);
        return inclusive.isNumber() ? new Bound(inclusive.asText(), exclusive.asBoolean(false)) : null;
    }

    private String text(String exclusiveSymbol, String inclusiveSymbol) {
        return (exclusive ? exclusiveSymbol : inclusiveSymbol) + " " + value;
    }
}
