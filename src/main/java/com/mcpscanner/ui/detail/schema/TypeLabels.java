package com.mcpscanner.ui.detail.schema;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class TypeLabels {

    private static final int MAX_DEPTH = 4;
    private static final String UNION = " | ";
    private static final String UNKNOWN = "";
    private static final int MAX_LABEL_LENGTH = 120;
    private static final String ELLIPSIS = "…";

    private final SchemaReferences references;
    private final Map<LabelKey, String> labelled = new HashMap<>();

    TypeLabels(SchemaReferences references) {
        this.references = references;
    }

    String of(JsonNode schema) {
        return of(schema, 0);
    }

    private String of(JsonNode schema, int depth) {
        if (depth > MAX_DEPTH) {
            return UNKNOWN;
        }
        LabelKey key = new LabelKey(schema, depth);
        String known = labelled.get(key);
        if (known == null) {
            known = capped(labelOf(schema, depth));
            labelled.put(key, known);
        }
        return known;
    }

    private String labelOf(JsonNode schema, int depth) {
        JsonNode node = references.resolve(schema).node();
        JsonNode type = node.path("type");
        if (type.isTextual()) {
            return named(type.asText(), node, depth);
        }
        if (type.isArray()) {
            return join(JsonText.elements(type).stream().map(name -> named(name.asText(), node, depth)).toList());
        }
        String union = union(node, depth);
        return union.isEmpty() ? inferred(node) : union;
    }

    private String named(String name, JsonNode node, int depth) {
        if (!"array".equals(name) || !node.path("items").isObject()) {
            return name;
        }
        String items = of(node.get("items"), depth + 1);
        return items.isEmpty() ? name : "array of " + items;
    }

    private String union(JsonNode node, int depth) {
        JsonNode branches = node.path("anyOf").isArray() ? node.get("anyOf") : node.path("oneOf");
        return join(JsonText.elements(branches).stream().map(branch -> of(branch, depth + 1)).toList());
    }

    private static String inferred(JsonNode node) {
        if (node.has("const")) {
            return kindOf(node.get("const"));
        }
        Set<String> enumKinds = JsonText.elements(node.path("enum")).stream().map(TypeLabels::kindOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (enumKinds.size() == 1) {
            return enumKinds.iterator().next();
        }
        if (node.path("properties").isObject()) {
            return "object";
        }
        return node.has("items") || node.has("prefixItems") ? "array" : UNKNOWN;
    }

    private static String kindOf(JsonNode value) {
        if (value.isNumber() && value.canConvertToExactIntegral()) {
            return "integer";
        }
        if (value.isNumber()) {
            return "number";
        }
        return value.getNodeType().name().toLowerCase(Locale.ROOT);
    }

    private static String join(List<String> labels) {
        return labels.stream().filter(label -> !label.isEmpty()).distinct().collect(Collectors.joining(UNION));
    }

    private static String capped(String label) {
        return label.length() <= MAX_LABEL_LENGTH
                ? label
                : label.substring(0, MAX_LABEL_LENGTH - ELLIPSIS.length()) + ELLIPSIS;
    }

    private record LabelKey(JsonNode schema, int depth) {
    }
}
