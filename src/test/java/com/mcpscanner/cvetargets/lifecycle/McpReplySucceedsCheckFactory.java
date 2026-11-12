package com.mcpscanner.cvetargets.lifecycle;

import com.mcpscanner.checks.api.CheckFactory;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;

public final class McpReplySucceedsCheckFactory implements CheckFactory {

    @Override
    public McpCheck create(CheckServices services) {
        return new McpReplySucceedsCheck();
    }
}
