package com.mcpscanner.checks.catalog.crossclientresponseleak;

import com.mcpscanner.checks.api.CheckFactory;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;

public final class CrossClientResponseLeakCheckFactory implements CheckFactory {

    @Override
    public McpCheck create(CheckServices services) {
        return new CrossClientResponseLeakCheck(services.eventLog(), services.authStrategy(), services.probeService());
    }
}
