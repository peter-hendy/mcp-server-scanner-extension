package com.mcpscanner.cvetargets.requests;

import com.mcpscanner.automation.CatalogLimit;
import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.ServerMetadata;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BaseRequestScopeTest {

    private static final ServerMetadata METADATA = new ServerMetadata(Map.of(), null, Map.of("tools", Map.of()));

    @Test
    void trimsTheDiscoveredCatalogKeepingTheServerMetadataAndReportsIt() {
        List<String> notices = new ArrayList<>();
        ConnectResult discovered = new ConnectResult(List.of(tool("a"), tool("b"), tool("c")), List.of(), List.of(),
                List.of(), METADATA);

        ConnectResult scanned = new BaseRequestScope(List.of(), new CatalogLimit(2), notices::add).limit(discovered);

        assertThat(scanned.tools()).extracting(McpToolDefinition::name).containsExactly("a", "b");
        assertThat(scanned.serverMetadata()).isSameAs(METADATA);
        assertThat(notices).containsExactly("catalog limited to 2 items per kind (tools 3 -> 2); seed requests are still sent");
    }

    @Test
    void anUnlimitedScopeHandsBackTheDiscoveredCatalogAsIs() {
        ConnectResult discovered = new ConnectResult(List.of(tool("a")), List.of(), List.of(), List.of(), METADATA);

        assertThat(BaseRequestScope.unlimited(List.of()).limit(discovered)).isSameAs(discovered);
    }

    private static McpToolDefinition tool(String name) {
        return new McpToolDefinition(name, "", "{\"type\":\"object\"}");
    }
}
