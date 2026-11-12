package com.mcpscanner.access;

import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.scan.MinedArguments;

import java.util.List;

@FunctionalInterface
public interface WriteReplayApproval {

    boolean approve(List<McpToolDefinition> writeTools);

    default boolean approve(List<McpToolDefinition> writeTools, MinedArguments minedArguments) {
        return approve(writeTools);
    }
}
