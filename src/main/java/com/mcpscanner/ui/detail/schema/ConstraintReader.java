package com.mcpscanner.ui.detail.schema;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ConstraintReader {

    private final ExtraProperties extraProperties;

    ConstraintReader(ExtraProperties extraProperties) {
        this.extraProperties = extraProperties;
    }

    List<SchemaConstraint> read(JsonNode schema) {
        List<SchemaConstraint> constraints = new ArrayList<>();
        if (schema.isBoolean() && !schema.asBoolean()) {
            constraints.add(SchemaConstraint.flag("never valid"));
        }
        if (schema.path("pattern").isTextual()) {
            constraints.add(SchemaConstraint.block("pattern", schema.get("pattern").asText()));
        }
        counted(constraints, "length", schema, "minLength", "maxLength");
        Bound.describe(Bound.lower(schema), Bound.upper(schema))
                .ifPresent(range -> constraints.add(SchemaConstraint.inline("range", range)));
        if (schema.has("multipleOf")) {
            constraints.add(SchemaConstraint.inline("multiple of", JsonText.plain(schema.get("multipleOf"))));
        }
        counted(constraints, "items", schema, "minItems", "maxItems");
        if (schema.path("uniqueItems").asBoolean(false)) {
            constraints.add(SchemaConstraint.flag("unique"));
        }
        counted(constraints, "properties", schema, "minProperties", "maxProperties");
        if (schema.has("const")) {
            constraints.add(SchemaConstraint.inline("const", JsonText.compact(schema.get("const"))));
        }
        KnownKeywords.COMBINATORS.forEach(combinator -> branches(schema, combinator).ifPresent(constraints::add));
        extraProperties.rule(schema).ifPresent(rule -> constraints.add(SchemaConstraint.inline("extra properties", rule)));
        return constraints;
    }

    private static void counted(List<SchemaConstraint> constraints, String label, JsonNode schema,
                                String minimumKey, String maximumKey) {
        Bound.describe(Bound.inclusive(schema.path(minimumKey)), Bound.inclusive(schema.path(maximumKey)))
                .ifPresent(bounds -> constraints.add(SchemaConstraint.inline(label, bounds)));
    }

    private static Optional<SchemaConstraint> branches(JsonNode schema, String combinator) {
        JsonNode branches = schema.path(combinator);
        return branches.isArray()
                ? Optional.of(SchemaConstraint.flag(combinator + ", " + branchCount(branches.size())))
                : Optional.empty();
    }

    private static String branchCount(int count) {
        return count + (count == 1 ? " branch" : " branches");
    }
}
