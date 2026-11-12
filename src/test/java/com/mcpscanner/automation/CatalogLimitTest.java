package com.mcpscanner.automation;

import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.scan.ScanInventory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogLimitTest {

    @Test
    void keepsTheFirstItemsOfEachKindUpToTheLimitAndSaysWhatItDropped() {
        ScanInventory inventory = new ScanInventory(tools(5), List.of(), List.of(),
                List.of(new McpPromptDefinition("summarise", "", List.of())));

        LimitedInventory limited = new CatalogLimit(3).applyTo(inventory);

        assertThat(limited.inventory().tools()).extracting(McpToolDefinition::name)
                .containsExactly("tool_0", "tool_1", "tool_2");
        assertThat(limited.inventory().prompts()).hasSize(1);
        assertThat(limited.notice()).contains(
                "catalog limited to 3 items per kind (tools 5 -> 3); seed requests are still sent");
    }

    @Test
    void saysNothingWhenTheCatalogFitsTheLimit() {
        LimitedInventory limited = new CatalogLimit(5).applyTo(ScanInventory.toolsOnly(tools(5)));

        assertThat(limited.inventory().tools()).hasSize(5);
        assertThat(limited.notice()).isEmpty();
    }

    @Test
    void anAbsentLimitKeepsEverything() {
        LimitedInventory limited = CatalogLimit.of(null).applyTo(ScanInventory.toolsOnly(tools(500)));

        assertThat(limited.inventory().tools()).hasSize(500);
        assertThat(limited.notice()).isEmpty();
    }

    private static List<McpToolDefinition> tools(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new McpToolDefinition("tool_" + index, "", "{\"type\":\"object\"}"))
                .toList();
    }
}
