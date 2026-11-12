package com.mcpscanner.ui.detail.schema;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

final class SchemaReferences {

    private static final String REF = "$ref";
    private static final String LOCAL = "#";
    private static final String LOCAL_PATH = "#/";

    private final JsonNode root;

    SchemaReferences(JsonNode root) {
        this.root = root;
    }

    ResolvedSchema resolve(JsonNode schema) {
        if (!hasReference(schema)) {
            return ResolvedSchema.direct(schema);
        }
        String ref = schema.get(REF).asText();
        return follow(ref, new HashSet<>())
                .map(target -> new ResolvedSchema(withSiblings(target, schema), ref, sourceLabel(ref), false))
                .orElseGet(() -> new ResolvedSchema(withSiblings(emptyObject(), schema), ref, sourceLabel(ref), true));
    }

    private Optional<JsonNode> follow(String ref, Set<String> seen) {
        if (!seen.add(ref)) {
            return Optional.empty();
        }
        return lookup(ref).flatMap(target -> hasReference(target)
                ? follow(target.get(REF).asText(), seen).map(next -> withSiblings(next, target))
                : Optional.of(target));
    }

    private Optional<JsonNode> lookup(String ref) {
        if (!ref.startsWith(LOCAL)) {
            return Optional.empty();
        }
        try {
            JsonNode target = root.at(JsonPointer.compile(ref.substring(LOCAL.length())));
            return target.isMissingNode() ? Optional.empty() : Optional.of(target);
        } catch (IllegalArgumentException malformedPointer) {
            return Optional.empty();
        }
    }

    private static JsonNode withSiblings(JsonNode target, JsonNode referencing) {
        if (!target.isObject()) {
            return target;
        }
        ObjectNode merged = emptyObject().setAll((ObjectNode) target);
        merged.remove(REF);
        referencing.fields().forEachRemaining(field -> {
            if (!REF.equals(field.getKey())) {
                merged.set(field.getKey(), field.getValue());
            }
        });
        return merged;
    }

    private static ObjectNode emptyObject() {
        return JsonNodeFactory.instance.objectNode();
    }

    private static String sourceLabel(String ref) {
        return ref.startsWith(LOCAL_PATH) ? ref.substring(LOCAL_PATH.length()) : ref;
    }

    private static boolean hasReference(JsonNode schema) {
        return schema != null && schema.isObject() && schema.path(REF).isTextual();
    }
}
