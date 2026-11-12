package com.mcpscanner.access.mining;

import com.mcpscanner.mcp.ToolSafety;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.mcp.McpToolDefinition;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record WriteToolOptIn(Mode mode, Set<String> selected) {

    public enum Mode {
        NONE,
        ALL,
        SELECTED
    }

    public static final WriteToolOptIn NONE = new WriteToolOptIn(Mode.NONE, Set.of());
    public static final WriteToolOptIn ALL = new WriteToolOptIn(Mode.ALL, Set.of());

    public WriteToolOptIn {
        selected = Set.copyOf(selected);
    }

    public static WriteToolOptIn selected(Set<String> tools) {
        return new WriteToolOptIn(Mode.SELECTED, tools);
    }

    Set<String> allowedTools(List<McpToolDefinition> tools) {
        return tools.stream().filter(ToolSafety::needsOptIn).map(McpToolDefinition::name).filter(this::allows)
                .collect(Collectors.toSet());
    }

    private boolean allows(String tool) {
        return switch (mode) {
            case NONE -> false;
            case ALL -> true;
            case SELECTED -> selected.contains(tool);
        };
    }

    String encoded() {
        ObjectNode node = McpObjectMapper.INSTANCE.createObjectNode().put("mode", mode.name());
        selected.forEach(node.putArray("selected")::add);
        return node.toString();
    }

    static Optional<WriteToolOptIn> decode(String encoded) {
        try {
            JsonNode node = McpObjectMapper.INSTANCE.readTree(encoded);
            Set<String> tools = new LinkedHashSet<>();
            node.path("selected").forEach(tool -> tools.add(tool.asText()));
            return Optional.of(new WriteToolOptIn(Mode.valueOf(node.path("mode").asText()), tools));
        } catch (Exception unreadable) {
            return Optional.empty();
        }
    }
}
