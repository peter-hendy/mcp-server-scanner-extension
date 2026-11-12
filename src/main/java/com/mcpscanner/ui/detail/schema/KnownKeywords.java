package com.mcpscanner.ui.detail.schema;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class KnownKeywords {

    static final List<String> COMBINATORS = List.of("anyOf", "oneOf", "allOf");

    static final Set<String> ROOT = Set.of("type", "properties", "required", "additionalProperties", "$schema",
            "$defs", "definitions", "$ref");

    static final Set<String> PROPERTY = Set.of("type", "format", "enum", "default", "const", "pattern",
            "minLength", "maxLength", "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf",
            "minItems", "maxItems", "uniqueItems", "minProperties", "maxProperties", "anyOf", "oneOf", "allOf",
            "description", "$ref", "properties", "required", "items", "prefixItems", "additionalProperties");

    static final Set<String> TOP_LEVEL_PROPERTY = union(PROPERTY, Set.of(McpHeaderParameter.KEYWORD));

    static final Set<String> NESTED = Set.of("type", "$ref", "items");

    static final Set<String> EXPANDED_NESTED = Set.of("type", "$ref", "items", "properties", "required");

    private KnownKeywords() {
    }

    private static Set<String> union(Set<String> base, Set<String> extra) {
        return Stream.concat(base.stream(), extra.stream()).collect(Collectors.toUnmodifiableSet());
    }
}
