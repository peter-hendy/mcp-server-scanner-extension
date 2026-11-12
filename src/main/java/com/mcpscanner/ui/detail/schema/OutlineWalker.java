package com.mcpscanner.ui.detail.schema;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class OutlineWalker {

    private static final int MAX_DEPTH = 8;
    private static final Set<String> ROOT_LINEAGE = Set.of("#");

    private final SchemaReferences references;
    private final TypeLabels types;
    private final ConstraintReader constraints;
    private final List<SchemaRow> rows = new ArrayList<>();
    private final OtherKeywords otherKeywords = new OtherKeywords();
    private final OutlineBudget budget = new OutlineBudget();

    OutlineWalker(SchemaReferences references, TypeLabels types, ConstraintReader constraints) {
        this.references = references;
        this.types = types;
        this.constraints = constraints;
    }

    void walkRoot(JsonNode root) {
        otherKeywords.reportUnknown(root, SchemaPath.ROOT, KnownKeywords.ROOT);
        otherKeywords.reportNested(root.path(ExtraProperties.KEYWORD), ExtraProperties.KEYWORD, false);
        walkProperties(root, 0, SchemaPath.ROOT, ROOT_LINEAGE);
        if (budget.exceeded()) {
            rows.add(RowFactory.truncation());
        }
    }

    List<SchemaRow> rows() {
        return List.copyOf(rows);
    }

    List<String> otherKeywords() {
        return otherKeywords.reported();
    }

    private void walkProperties(JsonNode parent, int depth, String parentPath, Set<String> lineage) {
        JsonNode properties = parent.path("properties");
        if (!properties.isObject()) {
            return;
        }
        Set<String> required = RequiredNames.of(parent);
        properties.fields().forEachRemaining(property -> walkRow(new RowPosition(property.getKey(),
                required.contains(property.getKey()), depth, SchemaPath.child(parentPath, property.getKey())),
                property.getValue(), lineage));
    }

    private void walkRow(RowPosition position, JsonNode schema, Set<String> ancestors) {
        if (!budget.admitsRow()) {
            return;
        }
        ResolvedSchema resolved = references.resolve(schema);
        Nesting nesting = Nesting.of(resolved, ancestors, references);
        boolean tooDeep = position.depth() >= MAX_DEPTH;
        boolean expands = nesting.hasContent() && !nesting.recursive() && !tooDeep
                && budget.admitsExpansion(resolved);
        rows.add(RowFactory.row(position, resolved, types, rowConstraints(resolved, nesting, tooDeep), expands));
        otherKeywords.reportProperty(resolved.node(), position.path(), expands && nesting.itemObject().isPresent(),
                McpHeaderParameter.appliesAt(position) ? KnownKeywords.TOP_LEVEL_PROPERTY : KnownKeywords.PROPERTY);
        if (expands) {
            walkChildren(position, nesting);
        }
    }

    private void walkChildren(RowPosition parent, Nesting nesting) {
        int depth = parent.depth() + 1;
        walkProperties(nesting.schema(), depth, parent.path(), nesting.lineage());
        nesting.itemObject().ifPresent(items ->
                walkProperties(items.node(), depth, SchemaPath.items(parent.path()), nesting.itemLineage()));
        List<JsonNode> tuple = nesting.tuple();
        for (int index = 0; index < tuple.size(); index++) {
            walkRow(new RowPosition(SchemaPath.positionName(index), false, depth,
                    SchemaPath.position(parent.path(), index)), tuple.get(index), nesting.lineage());
        }
    }

    private List<SchemaConstraint> rowConstraints(ResolvedSchema resolved, Nesting nesting, boolean tooDeep) {
        List<SchemaConstraint> rowConstraints = new ArrayList<>();
        if (resolved.unresolved()) {
            rowConstraints.add(SchemaConstraint.inline("unresolved $ref", resolved.ref()));
        }
        rowConstraints.addAll(constraints.read(resolved.node()));
        if (nesting.recursive()) {
            rowConstraints.add(SchemaConstraint.flag("recursive, not expanded"));
        } else if (tooDeep && nesting.hasContent()) {
            rowConstraints.add(SchemaConstraint.flag("deeper nesting not shown"));
        }
        return rowConstraints;
    }
}
