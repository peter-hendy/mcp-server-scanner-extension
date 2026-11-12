package com.mcpscanner.checks.catalog.commandinjection;

import com.mcpscanner.checks.api.CheckFactory;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.McpCheck;

public final class CommandInjectionCheckFactory implements CheckFactory {

    @Override
    public McpCheck create(CheckServices services) {
        return new CommandInjectionCheck(services.collaboratorWatch(), services.lateIssues(),
                services.burpNativeFields(), services.eventLog());
    }
}
