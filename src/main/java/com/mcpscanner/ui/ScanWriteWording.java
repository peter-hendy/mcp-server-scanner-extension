package com.mcpscanner.ui;

import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.scan.MinedArguments;

import java.awt.Component;
import java.util.List;
import java.util.function.Supplier;

record ScanWriteWording(Supplier<String> invoker) implements WriteToolWording {

    private static final String MINED_LEAD = " will invoke these tools with attacker-controlled inputs, starting "
            + "from the argument values below. " + MinedWriteLines.MINED_VALUES + " This can modify, create, or "
            + "delete data on the target — irreversible.";

    @Override
    public DestructiveScanConfirmation.Result ask(DestructiveScanPrompt prompt, Component parent,
                                                  List<McpToolDefinition> writes, int selectedCount) {
        return prompt.ask(parent, writes, selectedCount, invoker.get());
    }

    @Override
    public DestructiveScanConfirmation.Lines minedLines(List<McpToolDefinition> writes, int selectedCount,
                                                        MinedArguments mined) {
        return MinedWriteLines.of(writes, selectedCount, invoker.get() + MINED_LEAD,
                DestructiveScanConfirmation.Purpose.SCAN_WITH_MINED_VALUES, mined);
    }
}
