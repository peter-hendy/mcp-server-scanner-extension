package com.mcpscanner.ui;

import com.mcpscanner.mcp.ToolSafety;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.ToolAnnotations;

import java.util.List;

public final class DestructiveScanPolicy {

    private DestructiveScanPolicy() {
    }

    public static List<McpToolDefinition> nonReadOnly(List<McpToolDefinition> selected) {
        return selected.stream()
                .filter(ToolSafety::mayWrite)
                .toList();
    }

    public static boolean requiresConfirmation(List<McpToolDefinition> selected, boolean dontAskAgain) {
        return !dontAskAgain && !nonReadOnly(selected).isEmpty();
    }

    public static String formatToolLine(McpToolDefinition tool) {
        String suffix = tool.annotations().classify() == ToolAnnotations.Display.NOT_SPECIFIED
                ? "annotations missing"
                : "destructive";
        return "• " + tool.name() + " (" + suffix + ")";
    }

    public static String countLine(int flagged, int selected) {
        return flagged + " of the " + selected + " selected " + (selected == 1 ? "tool is" : "tools are")
                + " not annotated read-only:";
    }
}
