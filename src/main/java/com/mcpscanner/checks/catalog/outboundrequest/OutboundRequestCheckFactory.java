package com.mcpscanner.checks.catalog.outboundrequest;

import com.mcpscanner.checks.api.CheckFactory;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;

public final class OutboundRequestCheckFactory implements CheckFactory {

    @Override
    public McpCheck create(CheckServices services) {
        return new OutboundRequestCheck(services.eventLog(), services.collaboratorWatch(), services.lateIssues(),
                services.burpNativeFields());
    }
}
