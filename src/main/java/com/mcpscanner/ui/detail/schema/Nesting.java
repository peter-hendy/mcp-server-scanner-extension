package com.mcpscanner.ui.detail.schema;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

record Nesting(JsonNode schema, Optional<ResolvedSchema> itemObject, List<JsonNode> tuple, Set<String> lineage,
               boolean recursive) {

    static Nesting of(ResolvedSchema resolved, Set<String> ancestors, SchemaReferences references) {
        JsonNode schema = resolved.node();
        Set<String> lineage = extended(ancestors, resolved);
        Optional<ResolvedSchema> items = itemObject(schema, references);
        boolean recursive = repeats(ancestors, resolved) || items.filter(item -> repeats(lineage, item)).isPresent();
        return new Nesting(schema, items.filter(item -> !repeats(lineage, item)), tuple(schema), lineage, recursive);
    }

    boolean hasContent() {
        return hasProperties(schema) || itemObject.isPresent() || !tuple.isEmpty();
    }

    Set<String> itemLineage() {
        return itemObject.map(item -> extended(lineage, item)).orElse(lineage);
    }

    private static Optional<ResolvedSchema> itemObject(JsonNode schema, SchemaReferences references) {
        JsonNode items = schema.path("items");
        return items.isObject()
                ? Optional.of(references.resolve(items)).filter(item -> hasProperties(item.node()))
                : Optional.empty();
    }

    private static List<JsonNode> tuple(JsonNode schema) {
        JsonNode prefixItems = schema.path("prefixItems");
        return JsonText.elements(prefixItems.isArray() ? prefixItems : schema.path("items"));
    }

    private static boolean hasProperties(JsonNode schema) {
        return schema.path("properties").isObject() && !schema.path("properties").isEmpty();
    }

    private static boolean repeats(Set<String> ancestors, ResolvedSchema resolved) {
        return resolved.isReference() && ancestors.contains(resolved.ref());
    }

    private static Set<String> extended(Set<String> ancestors, ResolvedSchema resolved) {
        Set<String> extended = new HashSet<>(ancestors);
        if (resolved.isReference()) {
            extended.add(resolved.ref());
        }
        return Set.copyOf(extended);
    }
}
