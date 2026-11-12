package com.mcpscanner.scan;

import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.mcp.McpResourceTemplateDefinition;
import com.mcpscanner.mcp.McpToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScanInventoryTest {

    @Test
    void malformedItemsAreNeverScanned() {
        McpToolDefinition good = new McpToolDefinition("ok", null, "{}");

        ScanInventory inventory = new ScanInventory(List.of(good, new McpToolDefinition(null, null, "{}")),
                List.of(new McpResourceDefinition(null, "r", null, null)),
                List.of(new McpResourceTemplateDefinition(null, "t", null, null)),
                List.of(new McpPromptDefinition(null, null, List.of())));

        assertThat(inventory.tools()).containsExactly(good);
        assertThat(inventory.resources()).isEmpty();
        assertThat(inventory.resourceTemplates()).isEmpty();
        assertThat(inventory.prompts()).isEmpty();
        assertThat(inventory.totalCount()).isEqualTo(1);
    }
}
