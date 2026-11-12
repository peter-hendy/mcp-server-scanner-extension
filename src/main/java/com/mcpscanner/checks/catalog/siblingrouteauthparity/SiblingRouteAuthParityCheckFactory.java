package com.mcpscanner.checks.catalog.siblingrouteauthparity;

import com.mcpscanner.checks.api.CheckFactory;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;

public final class SiblingRouteAuthParityCheckFactory implements CheckFactory {

    @Override
    public McpCheck create(CheckServices services) {
        return new SiblingRouteAuthParityCheck(services.eventLog(), services.authStrategy(), services.probeService());
    }
}
