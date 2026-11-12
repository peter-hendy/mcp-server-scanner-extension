package com.mcpscanner.ui.detail.schema;

import com.fasterxml.jackson.databind.JsonNode;

record ResolvedSchema(JsonNode node, String ref, String refSource, boolean unresolved) {

    static ResolvedSchema direct(JsonNode node) {
        return new ResolvedSchema(node, null, null, false);
    }

    boolean isReference() {
        return ref != null;
    }
}
