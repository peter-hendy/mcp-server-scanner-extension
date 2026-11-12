package com.mcpscanner.checks.catalog.fixturebanner;

import com.mcpscanner.checks.api.CheckFactory;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;

public final class ServerBannerCheckFactory implements CheckFactory {

    @Override
    public McpCheck create(CheckServices services) {
        return new ServerBannerCheck();
    }
}
