package com.mcpscanner.ui.detail.schema;

public record SchemaConstraint(String label, String value, boolean block) {

    public static SchemaConstraint inline(String label, String value) {
        return new SchemaConstraint(label, value, false);
    }

    public static SchemaConstraint block(String label, String value) {
        return new SchemaConstraint(label, value, true);
    }

    public static SchemaConstraint flag(String label) {
        return new SchemaConstraint(label, null, false);
    }
}
