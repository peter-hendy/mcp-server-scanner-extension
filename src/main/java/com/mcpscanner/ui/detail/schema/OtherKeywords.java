package com.mcpscanner.ui.detail.schema;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class OtherKeywords {

    private final List<String> reported = new ArrayList<>();

    void reportUnknown(JsonNode schema, String path, Set<String> known) {
        schema.fields().forEachRemaining(field -> {
            if (!known.contains(field.getKey())) {
                reported.add(SchemaPath.child(path, field.getKey()) + ": " + JsonText.compact(field.getValue()));
            }
        });
    }

    void reportProperty(JsonNode schema, String path, boolean itemsExpanded, Set<String> known) {
        reportUnknown(schema, path, known);
        reportNested(schema.path("items"), SchemaPath.items(path), itemsExpanded);
        KnownKeywords.COMBINATORS.forEach(combinator -> reportBranches(schema.path(combinator), SchemaPath.child(path, combinator)));
        reportNested(schema.path(ExtraProperties.KEYWORD), SchemaPath.child(path, ExtraProperties.KEYWORD), false);
    }

    void reportNested(JsonNode schema, String path, boolean expanded) {
        if (!schema.isObject()) {
            return;
        }
        reportUnknown(schema, path, expanded ? KnownKeywords.EXPANDED_NESTED : KnownKeywords.NESTED);
        reportNested(schema.path("items"), SchemaPath.items(path), false);
    }

    List<String> reported() {
        return List.copyOf(reported);
    }

    private void reportBranches(JsonNode branches, String path) {
        List<JsonNode> elements = JsonText.elements(branches);
        for (int index = 0; index < elements.size(); index++) {
            reportNested(elements.get(index), SchemaPath.position(path, index), false);
        }
    }
}
