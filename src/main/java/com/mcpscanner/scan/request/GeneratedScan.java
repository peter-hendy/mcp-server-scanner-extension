package com.mcpscanner.scan.request;

import com.mcpscanner.mcp.McpToolDefinition;

import java.util.List;

public record GeneratedScan(List<ScanMessage> messages, List<McpToolDefinition> invokedTools) {

    public GeneratedScan {
        messages = List.copyOf(messages);
        invokedTools = List.copyOf(invokedTools);
    }
}
