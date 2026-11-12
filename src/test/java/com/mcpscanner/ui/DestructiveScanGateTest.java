package com.mcpscanner.ui;

import com.mcpscanner.config.ExtensionConfigStore;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.scan.MinedArguments;
import com.mcpscanner.testutil.InMemoryPersistedObject;
import org.junit.jupiter.api.Test;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DestructiveScanGateTest {

    private static final String ENDPOINT = "https://mcp.test/mcp";
    private static final McpToolDefinition DELETE_INVOICE = new McpToolDefinition("delete_invoice", "",
            "{\"type\":\"object\",\"properties\":{\"invoice_id\":{\"type\":\"string\",\"default\":\"inv-1\"},"
                    + "\"reason\":{\"type\":\"string\",\"default\":\"cleanup\"}},\"required\":[\"invoice_id\"]}");

    private final ExtensionConfigStore config = InMemoryPersistedObject.configStore();
    private final List<List<McpToolDefinition>> toolPrompts = new ArrayList<>();
    private final List<DestructiveScanConfirmation.Lines> linePrompts = new ArrayList<>();
    private DestructiveScanConfirmation.Result answer = new DestructiveScanConfirmation.Result(true, true);

    @Test
    void withoutMinedValuesDontAskAgainStillSkipsThePrompt() {
        config.setDontAskAgain(ENDPOINT, true);

        assertThat(gate().confirm(null, List.of(DELETE_INVOICE), ENDPOINT, MinedArguments.NONE)).isTrue();

        assertThat(toolPrompts).isEmpty();
        assertThat(linePrompts).isEmpty();
    }

    @Test
    void minedValuesInAWriteToolAlwaysPromptListingEveryValueWithMinedOnesMarked() {
        config.setDontAskAgain(ENDPOINT, true);
        answer = new DestructiveScanConfirmation.Result(false, false);

        boolean approved = gate().confirm(null, List.of(DELETE_INVOICE), ENDPOINT,
                new MinedArguments(Map.of("delete_invoice", Set.of("invoice_id"))));

        assertThat(approved).isFalse();
        assertThat(toolPrompts).isEmpty();
        assertThat(linePrompts).singleElement().satisfies(lines -> {
            assertThat(lines.offerDontAskAgain()).isFalse();
            assertThat(lines.lead()).contains("collected");
            assertThat(lines.items()).containsExactly(
                    "• delete_invoice (annotations missing): invoice_id=inv-1 (collected), reason=cleanup");
        });
    }

    @Test
    void approvingAMinedPromptNeverRecordsDontAskAgain() {
        gate().confirm(null, List.of(DELETE_INVOICE), ENDPOINT,
                new MinedArguments(Map.of("delete_invoice", Set.of("invoice_id"))));

        assertThat(config.dontAskAgain(ENDPOINT)).isFalse();
    }

    @Test
    void checkAccessAsksToReplayTheOwnersCallsInItsOwnWords() {
        boolean approved = checkAccessGate().confirm(null, List.of(DELETE_INVOICE), ENDPOINT, MinedArguments.NONE);

        assertThat(approved).isTrue();
        assertThat(toolPrompts).isEmpty();
        assertThat(linePrompts).singleElement().satisfies(lines -> {
            assertThat(lines.purpose()).isEqualTo(DestructiveScanConfirmation.Purpose.CHECK_ACCESS);
            assertThat(lines.purpose().title()).doesNotContain("scanning");
            assertThat(lines.purpose().proceedLabel()).isEqualTo("Proceed with Check access");
            assertThat(lines.lead()).contains("Check access will replay").doesNotContain("attacker-controlled");
            assertThat(lines.items()).containsExactly("• delete_invoice (annotations missing)");
        });
        assertThat(config.dontAskAgain(ENDPOINT)).isTrue();
    }

    @Test
    void checkAccessWithMinedValuesListsThemAndNeverOffersDontAskAgain() {
        config.setDontAskAgain(ENDPOINT, true);

        checkAccessGate().confirm(null, List.of(DELETE_INVOICE), ENDPOINT,
                new MinedArguments(Map.of("delete_invoice", Set.of("invoice_id"))));

        assertThat(linePrompts).singleElement().satisfies(lines -> {
            assertThat(lines.purpose()).isEqualTo(DestructiveScanConfirmation.Purpose.CHECK_ACCESS_WITH_MINED_VALUES);
            assertThat(lines.offerDontAskAgain()).isFalse();
            assertThat(lines.lead()).doesNotContain("attacker-controlled");
            assertThat(lines.items()).containsExactly(
                    "• delete_invoice (annotations missing): invoice_id=inv-1 (collected), reason=cleanup");
        });
    }

    @Test
    void scanKeepsItsDestructiveScanningWording() {
        assertThat(DestructiveScanConfirmation.Purpose.SCAN.title()).isEqualTo("Enable destructive scanning?");
        assertThat(DestructiveScanConfirmation.Purpose.SCAN.proceedLabel()).isEqualTo("Proceed with scan");
    }

    private DestructiveScanGate checkAccessGate() {
        return DestructiveScanGate.forCheckAccess(config, recordingPrompt());
    }

    private DestructiveScanGate gate() {
        return new DestructiveScanGate(config, recordingPrompt());
    }

    private DestructiveScanPrompt recordingPrompt() {
        return new DestructiveScanPrompt() {
            @Override
            public DestructiveScanConfirmation.Result ask(Component parent, List<McpToolDefinition> nonReadOnly,
                                                          int selectedCount, String invoker) {
                toolPrompts.add(nonReadOnly);
                return answer;
            }

            @Override
            public DestructiveScanConfirmation.Result askLines(Component parent,
                                                               DestructiveScanConfirmation.Lines lines) {
                linePrompts.add(lines);
                return answer;
            }
        };
    }
}
