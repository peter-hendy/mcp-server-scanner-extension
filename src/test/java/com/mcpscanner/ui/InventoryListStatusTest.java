package com.mcpscanner.ui;

import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.client.ListError;
import com.mcpscanner.client.ListOutcome;
import com.mcpscanner.client.ListTruncation;
import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.ServerMetadata;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryListStatusTest {

    private final InventoryPanels inventory = new InventoryPanels();

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void aFailedListShowsItsMethodAndErrorOnItsTab() {
        inventory.populate(result(ListOutcome.complete(List.of()),
                ListOutcome.failed(List.of(), new ListError.JsonRpcError(-32601, "Method not found"))));

        assertThat(inventory.prompts().listStatus().isVisible()).isTrue();
        assertThat(inventory.prompts().listStatus().message())
                .isEqualTo("prompts/list failed: JSON-RPC error -32601: Method not found");
        assertThat(inventory.tools().listStatus().isVisible()).isFalse();
        assertThat(inventory.resources().listStatus().isVisible()).isFalse();
    }

    @Test
    void aTruncatedListSaysItIsIncomplete() {
        inventory.populate(result(ListOutcome.truncated(List.of(new McpToolDefinition("a", null, "{}")),
                new ListTruncation.RepeatedCursor("c1")), ListOutcome.complete(List.of())));

        assertThat(inventory.tools().listStatus().isVisible()).isTrue();
        assertThat(inventory.tools().listStatus().message())
                .isEqualTo("List incomplete: stopped on repeated cursor \"c1\"");
    }

    @Test
    void clearingHidesEveryBanner() {
        inventory.populate(result(ListOutcome.complete(List.of()),
                ListOutcome.failed(List.of(), new ListError.HttpStatus(500))));

        inventory.clear();

        assertThat(inventory.prompts().listStatus().isVisible()).isFalse();
    }

    @Test
    void theNextConnectReplacesTheBanner() {
        inventory.populate(result(ListOutcome.complete(List.of()),
                ListOutcome.failed(List.of(), new ListError.HttpStatus(500))));

        inventory.populate(result(ListOutcome.complete(List.of()), ListOutcome.complete(List.of())));

        assertThat(inventory.prompts().listStatus().isVisible()).isFalse();
    }

    @Test
    void aFailedListSaysItCouldNotBeListedInsteadOfClaimingNoItems() {
        ListError error = new ListError.JsonRpcError(-32601, "Method not found");
        inventory.setAvailability(InventoryView.Availability.CONNECTED);

        inventory.populate(new ConnectResult(ListOutcome.failed(List.of(), error), ListOutcome.failed(List.of(), error),
                ListOutcome.failed(List.of(), error), ListOutcome.failed(List.of(), error), ServerMetadata.empty()));

        assertThat(inventory.tools().view().emptyMessage())
                .isEqualTo("The server couldn't list its tools. The message above says why.");
        assertThat(inventory.resources().view().emptyMessage()).startsWith("The server couldn't list its resources.");
        assertThat(inventory.templates().view().emptyMessage()).startsWith("The server couldn't list its ");
        assertThat(inventory.prompts().view().emptyMessage()).startsWith("The server couldn't list its prompts.");
    }

    @Test
    void aSuccessfulEmptyListStillSaysTheServerExposesNone() {
        inventory.setAvailability(InventoryView.Availability.CONNECTED);
        inventory.populate(result(ListOutcome.complete(List.of()),
                ListOutcome.failed(List.of(), new ListError.HttpStatus(500))));

        inventory.populate(result(ListOutcome.complete(List.of()), ListOutcome.complete(List.of())));

        assertThat(inventory.prompts().view().emptyMessage()).isEqualTo("This server exposes no prompts.");
    }

    private static ConnectResult result(ListOutcome<McpToolDefinition> tools,
                                        ListOutcome<McpPromptDefinition> prompts) {
        return new ConnectResult(tools, ListOutcome.complete(List.of()), ListOutcome.complete(List.of()), prompts,
                ServerMetadata.empty());
    }
}
