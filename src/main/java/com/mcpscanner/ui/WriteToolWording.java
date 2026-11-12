package com.mcpscanner.ui;

import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.scan.MinedArguments;

import java.awt.Component;
import java.util.List;

interface WriteToolWording {

    DestructiveScanConfirmation.Result ask(DestructiveScanPrompt prompt, Component parent,
                                           List<McpToolDefinition> writes, int selectedCount);

    DestructiveScanConfirmation.Lines minedLines(List<McpToolDefinition> writes, int selectedCount,
                                                 MinedArguments mined);
}
