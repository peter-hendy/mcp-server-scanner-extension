package com.mcpscanner.ui.detail.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.util.List;

public final class SchemaOutline {

    private final List<SchemaRow> rows;
    private final SchemaSummary summary;
    private final List<String> otherKeywords;

    private SchemaOutline(List<SchemaRow> rows, SchemaSummary summary, List<String> otherKeywords) {
        this.rows = rows;
        this.summary = summary;
        this.otherKeywords = otherKeywords;
    }

    public static SchemaOutline of(JsonNode schema) {
        JsonNode original = schema == null ? MissingNode.getInstance() : schema;
        SchemaReferences references = new SchemaReferences(original);
        JsonNode root = references.resolve(original).node();
        TypeLabels types = new TypeLabels(references);
        ExtraProperties extraProperties = new ExtraProperties(types);
        OutlineWalker walker = new OutlineWalker(references, types, new ConstraintReader(extraProperties));
        walker.walkRoot(root);
        SchemaSummary summary = new SchemaSummary(propertyCount(root), RequiredNames.of(root).size(),
                extraProperties.summary(root));
        return new SchemaOutline(walker.rows(), summary, walker.otherKeywords());
    }

    private static int propertyCount(JsonNode root) {
        JsonNode properties = root.path("properties");
        return properties.isObject() ? properties.size() : 0;
    }

    public List<SchemaRow> rows() {
        return rows;
    }

    public SchemaSummary summary() {
        return summary;
    }

    public List<String> otherKeywords() {
        return otherKeywords;
    }

    public int parameterCount() {
        return summary.parameterCount();
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public boolean hasContent() {
        return !rows.isEmpty() || !otherKeywords.isEmpty();
    }
}
