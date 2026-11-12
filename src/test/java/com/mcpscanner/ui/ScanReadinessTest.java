package com.mcpscanner.ui;

import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.scan.ScanInventory;
import com.mcpscanner.scan.ScanMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ScanReadinessTest {

    private static final ScanInventory TWO_TOOLS_ONE_RESOURCE = new ScanInventory(
            List.of(new McpToolDefinition("a", "", "{}"), new McpToolDefinition("b", "", "{}")),
            List.of(new McpResourceDefinition("docs://x", "x", "", "text/plain")), List.of(), List.of());

    @Test
    void disconnectedScanExplainsThatItNeedsAConnection() {
        ScanReadiness readiness = new ScanReadiness(false, TWO_TOOLS_ONE_RESOURCE, 5);

        assertThat(readiness.blocker()).contains("Connect to an MCP server first");
        assertThat(readiness.tooltip(ScanMode.MCP_ONLY)).isEqualTo("Connect to an MCP server first");
    }

    @Test
    void connectedScanThatWouldSendNothingAsksForAnItemOrARequestFamily() {
        ScanReadiness readiness = new ScanReadiness(true, ScanInventory.empty(), 0);

        assertThat(readiness.blocker())
                .contains("Tick a tool, resource, template or prompt, or turn on a request family under Requests");
        assertThat(readiness.shortBlocker()).contains("Tick items to scan");
        assertThat(readiness.buttonLabel()).isEqualTo("Scan");
    }

    @ParameterizedTest
    @EnumSource(ScanMode.class)
    void connectedScanWithNothingTickedStillSendsTheTickedRequestFamilies(ScanMode mode) {
        ScanReadiness readiness = new ScanReadiness(true, ScanInventory.empty(), 3);

        assertThat(readiness.blocker()).isEmpty();
        assertThat(readiness.shortBlocker()).isEmpty();
        assertThat(readiness.buttonLabel()).isEqualTo("Scan 3 requests");
        assertThat(readiness.summary()).isEqualTo("3 requests");
        assertThat(readiness.tooltip(mode))
                .isEqualTo("Send 3 requests (no items ticked) with " + ScanModeText.of(mode).label());
    }

    @Test
    void tickedItemsThatWouldSendNothingAreBlocked() {
        ScanReadiness readiness = new ScanReadiness(true, TWO_TOOLS_ONE_RESOURCE, 0);

        assertThat(readiness.blocker()).isPresent();
        assertThat(readiness.buttonLabel()).isEqualTo("Scan");
    }

    @ParameterizedTest
    @EnumSource(ScanMode.class)
    void readyScanNamesWhatWillBeScannedAndTheMode(ScanMode mode) {
        ScanReadiness readiness = new ScanReadiness(true, TWO_TOOLS_ONE_RESOURCE, 5);

        assertThat(readiness.blocker()).isEmpty();
        assertThat(readiness.buttonLabel()).isEqualTo("Scan 3 items");
        assertThat(readiness.summary()).isEqualTo("3 items (2 tools, 1 resource)");
        assertThat(readiness.tooltip(mode))
                .isEqualTo("Scan the 3 selected items (2 tools, 1 resource) with " + ScanModeText.of(mode).label());
    }

    @Test
    void summaryUsesSingularAndSkipsEmptyKinds() {
        ScanInventory onePrompt = new ScanInventory(List.of(), List.of(), List.of(),
                List.of(new McpPromptDefinition("p", "", List.of())));

        assertThat(ScanReadiness.describe(onePrompt)).isEqualTo("1 item (1 prompt)");
    }

    @Test
    void connectedScanWhoseRequestsAreStillBeingCountedWaits() {
        ScanReadiness readiness = new ScanReadiness(true, ScanInventory.empty(), Optional.empty());

        assertThat(readiness.blocker()).contains("Counting requests…");
        assertThat(readiness.tooltip(ScanMode.MCP_ONLY)).isEqualTo("Counting requests…");
        assertThat(readiness.shortBlocker()).isEmpty();
        assertThat(readiness.buttonLabel()).isEqualTo("Scan");
    }

    @Test
    void tickedItemsStillBeingCountedKeepTheItemLabel() {
        ScanReadiness readiness = new ScanReadiness(true, TWO_TOOLS_ONE_RESOURCE, Optional.empty());

        assertThat(readiness.blocker()).contains("Counting requests…");
        assertThat(readiness.buttonLabel()).isEqualTo("Scan 3 items");
    }

    @Test
    void disconnectedScanAsksForAConnectionEvenBeforeAnyCount() {
        ScanReadiness readiness = new ScanReadiness(false, TWO_TOOLS_ONE_RESOURCE, Optional.empty());

        assertThat(readiness.blocker()).contains("Connect to an MCP server first");
    }
}
