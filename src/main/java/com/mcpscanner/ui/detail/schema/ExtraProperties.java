package com.mcpscanner.ui.detail.schema;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

final class ExtraProperties {

    static final String KEYWORD = "additionalProperties";

    private final TypeLabels types;

    ExtraProperties(TypeLabels types) {
        this.types = types;
    }

    Optional<String> rule(JsonNode schema) {
        JsonNode extra = schema.path(KEYWORD);
        if (extra.isBoolean()) {
            return Optional.of(extra.asBoolean() ? "allowed" : "not allowed");
        }
        if (!extra.isObject()) {
            return Optional.empty();
        }
        String type = types.of(extra);
        return Optional.of(type.isEmpty() ? "allowed" : "allowed (" + type + ")");
    }

    String summary(JsonNode schema) {
        return rule(schema).map(rule -> "additional properties: " + rule).orElse("no extra properties rule");
    }
}
