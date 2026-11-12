package com.mcpscanner.checks.catalog.fixtureresend;

import com.mcpscanner.checks.api.CheckFactory;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;

public final class ResendSucceedsCheckFactory implements CheckFactory {

    @Override
    public McpCheck create(CheckServices services) {
        return new ResendSucceedsCheck();
    }
}
