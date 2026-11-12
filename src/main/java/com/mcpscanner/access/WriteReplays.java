package com.mcpscanner.access;

import com.mcpscanner.checks.api.AccessItemKey;
import com.mcpscanner.checks.api.ReplayPermission;
import com.mcpscanner.mcp.McpToolDefinition;

import java.util.List;

public record WriteReplays(WriteReplayConfirmations confirmations, ReplayPermission permission) {

    void confirm(List<McpToolDefinition> tools) {
        confirmations.confirm(tools);
    }

    boolean permits(AccessItemKey item) {
        return permission.permitsReplay(item);
    }
}
