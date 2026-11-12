package com.mcpscanner.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class ParamHeaderSchema {

    private static final String ANNOTATION = "x-mcp-header";
    private static final String PROPERTIES = "properties";
    private static final Pattern TOKEN = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");
    private static final Set<String> PRIMITIVE_TYPES = Set.of("string", "integer", "boolean");
    private static final Set<String> SCHEMA_MAPS =
            Set.of(PROPERTIES, "patternProperties", "$defs", "definitions", "dependentSchemas");
    private static final Set<String> SCHEMA_LISTS = Set.of("items", "prefixItems", "allOf", "anyOf", "oneOf");
    private static final Set<String> SCHEMAS = Set.of("additionalProperties", "additionalItems",
            "unevaluatedProperties", "unevaluatedItems", "not", "if", "then", "else", "contains", "propertyNames",
            "contentSchema");

    private final List<ParamHeaderBinding> bindings = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();
    private final Set<String> claimedNames = new HashSet<>();

    private ParamHeaderSchema(JsonNode inputSchema) {
        visit(inputSchema, Optional.of(List.of()));
    }

    public static List<ParamHeaderBinding> bindingsIn(JsonNode inputSchema) {
        return List.copyOf(new ParamHeaderSchema(inputSchema).bindings);
    }

    public static List<String> problemsIn(JsonNode inputSchema) {
        return List.copyOf(new ParamHeaderSchema(inputSchema).problems);
    }

    private void visit(JsonNode schema, Optional<List<String>> propertyPath) {
        if (schema == null || !schema.isObject()) {
            return;
        }
        if (schema.has(ANNOTATION)) {
            annotated(schema, propertyPath);
        }
        schema.fields().forEachRemaining(field -> visitKeyword(field.getKey(), field.getValue(), propertyPath));
    }

    private void visitKeyword(String keyword, JsonNode value, Optional<List<String>> propertyPath) {
        if (SCHEMA_MAPS.contains(keyword) && value.isObject()) {
            value.fields().forEachRemaining(entry -> visit(entry.getValue(), PROPERTIES.equals(keyword)
                    ? propertyPath.map(path -> appended(path, entry.getKey())) : Optional.empty()));
        } else if (SCHEMA_LISTS.contains(keyword)) {
            if (value.isArray()) {
                value.forEach(child -> visit(child, Optional.empty()));
            } else {
                visit(value, Optional.empty());
            }
        } else if (SCHEMAS.contains(keyword)) {
            visit(value, Optional.empty());
        }
    }

    private void annotated(JsonNode schema, Optional<List<String>> propertyPath) {
        JsonNode name = schema.get(ANNOTATION);
        if (!name.isTextual() || !TOKEN.matcher(name.asText()).matches()) {
            problems.add(ANNOTATION + " " + name + " isn't a valid header name");
        } else if (propertyPath.filter(path -> !path.isEmpty()).isEmpty()) {
            problems.add(ANNOTATION + " " + name.asText() + " is on a value that isn't reached through properties alone");
        } else if (!isPrimitive(schema)) {
            problems.add(ANNOTATION + " " + name.asText() + " is on " + String.join(".", propertyPath.get())
                    + ", which isn't a string, integer or boolean");
        } else if (!claimedNames.add(name.asText().toLowerCase(Locale.ROOT))) {
            problems.add("more than one " + ANNOTATION + " is named " + name.asText());
        } else {
            bindings.add(new ParamHeaderBinding(McpHeaders.PARAM_PREFIX + name.asText(), propertyPath.get()));
        }
    }

    private static boolean isPrimitive(JsonNode propertySchema) {
        JsonNode type = propertySchema.path("type");
        if (type.isTextual()) {
            return PRIMITIVE_TYPES.contains(type.asText());
        }
        List<String> nonNullTypes = new ArrayList<>();
        type.forEach(entry -> {
            if (entry.isTextual() && !entry.asText().equals("null")) {
                nonNullTypes.add(entry.asText());
            }
        });
        return nonNullTypes.size() == 1 && PRIMITIVE_TYPES.contains(nonNullTypes.get(0));
    }

    private static List<String> appended(List<String> path, String property) {
        List<String> appended = new ArrayList<>(path);
        appended.add(property);
        return List.copyOf(appended);
    }
}
