package com.mcpscanner.ui.detail.schema;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

final class RowFactory {

    private static final String TRUNCATED_NAME = "…";
    private static final String TRUNCATED_DESCRIPTION = "Schema too large to show in full — see Raw.";

    private RowFactory() {
    }

    static SchemaRow truncation() {
        return new SchemaRow(0, TRUNCATED_NAME, false, "", null, List.of(), null, List.of(), TRUNCATED_DESCRIPTION,
                null, false, null);
    }

    static SchemaRow row(RowPosition position, ResolvedSchema resolved, TypeLabels types,
                         List<SchemaConstraint> constraints, boolean hasChildren) {
        JsonNode schema = resolved.node();
        return new SchemaRow(position.depth(), position.name(), position.required(), types.of(schema),
                textOrNull(schema.path("format")), enumValues(schema), compactOrNull(schema.path("default")),
                constraints, description(schema), resolved.refSource(), hasChildren,
                McpHeaderParameter.headerOrNull(position, schema));
    }

    private static List<String> enumValues(JsonNode schema) {
        return JsonText.elements(schema.path("enum")).stream().map(JsonText::plain).toList();
    }

    private static String description(JsonNode schema) {
        String description = textOrNull(schema.path("description"));
        return description == null || description.isBlank() ? null : description;
    }

    private static String textOrNull(JsonNode value) {
        return value.isMissingNode() ? null : JsonText.plain(value);
    }

    private static String compactOrNull(JsonNode value) {
        return value.isMissingNode() ? null : JsonText.compact(value);
    }
}
