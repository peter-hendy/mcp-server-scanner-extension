package com.mcpscanner.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.scan.JsonSchemaDefaults;
import com.mcpscanner.scan.MinedArguments;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class MinedWriteLines {

    static final String MINED_VALUES = "Values marked (collected) are real IDs collected from the server's replies.";

    private MinedWriteLines() {
    }

    static DestructiveScanConfirmation.Lines of(List<McpToolDefinition> writes, int selectedCount, String lead,
                                                DestructiveScanConfirmation.Purpose purpose, MinedArguments mined) {
        return new DestructiveScanConfirmation.Lines(lead,
                DestructiveScanPolicy.countLine(writes.size(), selectedCount),
                writes.stream().map(tool -> line(tool, mined.of(tool))).toList(), purpose);
    }

    private static String line(McpToolDefinition tool, Set<String> mined) {
        List<String> values = new ArrayList<>();
        arguments(tool).fields().forEachRemaining(argument -> values.add(argument.getKey() + "="
                + text(argument.getValue()) + (mined.contains(argument.getKey()) ? " (collected)" : "")));
        String toolLine = DestructiveScanPolicy.formatToolLine(tool);
        return values.isEmpty() ? toolLine : toolLine + ": " + String.join(", ", values);
    }

    private static JsonNode arguments(McpToolDefinition tool) {
        try {
            return McpObjectMapper.INSTANCE.readTree(JsonSchemaDefaults.buildDefaultArgumentsJson(tool.inputSchema()));
        } catch (Exception unreadable) {
            return McpObjectMapper.INSTANCE.createObjectNode();
        }
    }

    private static String text(JsonNode value) {
        return value.isValueNode() ? value.asText() : value.toString();
    }
}
