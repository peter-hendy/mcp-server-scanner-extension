package com.mcpscanner.checks.catalog.downstreamrouteescape;

import com.mcpscanner.checks.api.CheckFactory;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;

public final class DownstreamRouteEscapeCheckFactory implements CheckFactory {

    @Override
    public McpCheck create(CheckServices services) {
        return new DownstreamRouteEscapeCheck(services.eventLog());
    }
}
