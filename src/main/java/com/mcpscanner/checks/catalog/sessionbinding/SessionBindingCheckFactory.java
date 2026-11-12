package com.mcpscanner.checks.catalog.sessionbinding;

import com.mcpscanner.checks.api.CheckFactory;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;

public final class SessionBindingCheckFactory implements CheckFactory {

    @Override
    public McpCheck create(CheckServices services) {
        return new SessionBindingCheck(services.eventLog(), services.authStrategy(), services.principals(),
                services.accessLedger());
    }
}
