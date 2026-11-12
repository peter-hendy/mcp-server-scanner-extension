package com.mcpscanner.cvetargets.run;

import com.mcpscanner.checks.api.InsertionPointSource;
import com.mcpscanner.cvetargets.requests.BaseRequestPlanner;
import com.mcpscanner.cvetargets.requests.BaseRequestSource;
import com.mcpscanner.cvetargets.requests.DiscoveredInventoryRequestSource;
import com.mcpscanner.cvetargets.requests.GeneratedRequestPlanner;
import com.mcpscanner.mcp.BurpNativeJsonRpcFields;
import com.mcpscanner.scan.McpInsertionPointProvider;
import com.mcpscanner.scan.field.TemplateExpansionRegistry;

record GeneratedBaseRequests(BaseRequestPlanner planner, InsertionPointSource insertionPoints) {

    static GeneratedBaseRequests create() {
        TemplateExpansionRegistry templateExpansions = new TemplateExpansionRegistry();
        return new GeneratedBaseRequests(new GeneratedRequestPlanner(templateExpansions),
                new McpInsertionPointProvider(() -> true, templateExpansions, BurpNativeJsonRpcFields.ABSENT)::provideInsertionPoints);
    }

    BaseRequestSource source() {
        return new DiscoveredInventoryRequestSource(planner);
    }
}
