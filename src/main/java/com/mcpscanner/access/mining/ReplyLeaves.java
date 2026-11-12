package com.mcpscanner.access.mining;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.JsonLeaves;
import com.mcpscanner.mcp.McpObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ReplyLeaves {

    private final List<ReplyField> fields = new ArrayList<>();

    private ReplyLeaves() {
    }

    static List<ReplyField> of(JsonNode result) {
        ReplyLeaves collected = new ReplyLeaves();
        collected.walk(result.path("structuredContent"), "structuredContent");
        collected.embedded(result.path("content"), "content", "text");
        collected.embedded(result.path("content"), "content", "resource", "text");
        collected.embedded(result.path("contents"), "contents", "text");
        collected.embedded(result.path("messages"), "messages", "content", "text");
        return List.copyOf(collected.fields);
    }

    private void embedded(JsonNode items, String name, String... textPath) {
        for (int index = 0; index < items.size(); index++) {
            JsonNode text = items.get(index);
            for (String step : textPath) {
                text = text.path(step);
            }
            String location = name + "[" + index + "]." + String.join(".", textPath);
            parsed(text).ifPresent(json -> walk(json, location));
        }
    }

    private void walk(JsonNode node, String location) {
        JsonLeaves.under(node, List.of()).stream()
                .filter(leaf -> !leaf.name().isEmpty())
                .map(leaf -> new ReplyField(location + "." + leaf.pathText(), leaf))
                .forEach(fields::add);
    }

    private static Optional<JsonNode> parsed(JsonNode text) {
        if (!text.isTextual()) {
            return Optional.empty();
        }
        try {
            JsonNode json = McpObjectMapper.INSTANCE.readTree(text.asText());
            return json != null && json.isContainerNode() ? Optional.of(json) : Optional.empty();
        } catch (Exception notJson) {
            return Optional.empty();
        }
    }
}
