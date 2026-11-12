package com.mcpscanner.ui;

import com.mcpscanner.mcp.McpToolDefinition;

import java.awt.Component;
import java.util.List;

@FunctionalInterface
public interface DestructiveScanPrompt {

    DestructiveScanPrompt MODAL = DestructiveScanConfirmation::prompt;

    DestructiveScanConfirmation.Result ask(Component parent, List<McpToolDefinition> nonReadOnly, int selectedCount,
                                           String invoker);

    default DestructiveScanConfirmation.Result askLines(Component parent, DestructiveScanConfirmation.Lines lines) {
        return DestructiveScanConfirmation.promptLines(parent, lines);
    }
}
