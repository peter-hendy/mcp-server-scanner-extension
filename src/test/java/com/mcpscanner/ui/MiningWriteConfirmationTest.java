package com.mcpscanner.ui;

import com.fasterxml.jackson.databind.node.TextNode;
import com.mcpscanner.access.mining.MiningCall;
import com.mcpscanner.config.ExtensionConfigStore;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.testutil.InMemoryPersistedObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MiningWriteConfirmationTest {

    private static final String ENDPOINT = "https://mcp.test/mcp";
    private static final McpToolDefinition ARCHIVE = new McpToolDefinition("campaigns_archive", "", "{}");
    private static final McpToolDefinition ARCHIVE_WITH_NOTE = new McpToolDefinition("campaigns_archive", "",
            "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"},"
                    + "\"note\":{\"type\":\"string\",\"default\":\"archived by test\"}},\"required\":[\"id\"]}");

    private final ExtensionConfigStore config = InMemoryPersistedObject.configStore();
    private final List<DestructiveScanConfirmation.Lines> shown = new ArrayList<>();

    @Test
    void listsEveryWriteCallWithEveryArgumentItWillSendAndTheUserBeingMined() {
        approval(new DestructiveScanConfirmation.Result(true, false)).approve("alice", List.of(
                new MiningCall.ToolCall(ARCHIVE_WITH_NOTE, Map.of("id", TextNode.valueOf("c0a11ce0-0001"))),
                new MiningCall.ToolCall(ARCHIVE, Map.of())));

        assertThat(shown).singleElement().satisfies(lines -> {
            assertThat(lines.lead()).contains("as alice").contains("real IDs").contains("asked again before any later call carries an ID not shown yet");
            assertThat(lines.count()).isEqualTo("2 write calls to 1 tool will be sent while collecting IDs:");
            assertThat(lines.purpose().title()).isEqualTo("Allow write calls while collecting IDs?");
            assertThat(lines.purpose().proceedLabel()).isEqualTo("Proceed with collecting");
            assertThat(lines.items()).containsExactly(
                    "• campaigns_archive id=c0a11ce0-0001, note=archived by test",
                    "• campaigns_archive (no arguments)");
        });
    }

    @Test
    void cancellingRefusesTheCalls() {
        assertThat(approval(new DestructiveScanConfirmation.Result(false, false))
                .approve("alice", List.of(new MiningCall.ToolCall(ARCHIVE, Map.of())))).isFalse();
    }

    @Test
    void alwaysShowsTheIdsEvenWhenScanWasToldNotToAskAgainForTheServer() {
        config.setDontAskAgain(ENDPOINT, true);

        boolean approved = approval(new DestructiveScanConfirmation.Result(false, false))
                .approve("alice", List.of(new MiningCall.ToolCall(ARCHIVE, Map.of())));

        assertThat(approved).isFalse();
        assertThat(shown).hasSize(1);
    }

    @Test
    void offersNoDontAskAgainAndNeverRecordsOne() {
        approval(new DestructiveScanConfirmation.Result(true, true))
                .approve("alice", List.of(new MiningCall.ToolCall(ARCHIVE, Map.of())));
        approval(new DestructiveScanConfirmation.Result(true, false))
                .approve("alice", List.of(new MiningCall.ToolCall(ARCHIVE, Map.of())));

        assertThat(shown).hasSize(2).allSatisfy(lines -> assertThat(lines.offerDontAskAgain()).isFalse());
        assertThat(config.dontAskAgain(ENDPOINT)).isFalse();
    }

    private MiningWriteConfirmation approval(DestructiveScanConfirmation.Result answer) {
        return new MiningWriteConfirmation((parent, lines) -> {
            shown.add(lines);
            return answer;
        }, null);
    }
}
