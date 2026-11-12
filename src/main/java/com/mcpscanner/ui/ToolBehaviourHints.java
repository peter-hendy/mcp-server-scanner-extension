package com.mcpscanner.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.ToolAnnotations;

import java.util.Optional;
import java.util.stream.Stream;

record ToolBehaviourHints(
        Optional<Boolean> readOnly,
        Optional<Boolean> destructive,
        Optional<Boolean> idempotent,
        Optional<Boolean> openWorld) {

    static ToolBehaviourHints of(McpToolDefinition tool) {
        JsonNode declared = tool.raw().path("annotations");
        ToolAnnotations parsed = tool.annotations();
        return new ToolBehaviourHints(
                hint(declared, "readOnlyHint", parsed.readOnlyHint()),
                hint(declared, "destructiveHint", parsed.destructiveHint()),
                hint(declared, "idempotentHint", parsed.idempotentHint()),
                hint(declared, "openWorldHint", parsed.openWorldHint()));
    }

    boolean noneDeclared() {
        return Stream.of(readOnly, destructive, idempotent, openWorld).allMatch(Optional::isEmpty);
    }

    private static Optional<Boolean> hint(JsonNode annotations, String key, Boolean parsed) {
        JsonNode value = annotations.path(key);
        return value.isBoolean() ? Optional.of(value.booleanValue()) : Optional.ofNullable(parsed);
    }
}
