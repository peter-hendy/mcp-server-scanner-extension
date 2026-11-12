package com.mcpscanner.ui.detail.schema;

import java.util.List;

public record SchemaRow(int depth, String name, boolean required, String type, String format,
                        List<String> enumValues, String defaultValue, List<SchemaConstraint> constraints,
                        String description, String refSource, boolean hasChildren, String mcpHeader) {
}
