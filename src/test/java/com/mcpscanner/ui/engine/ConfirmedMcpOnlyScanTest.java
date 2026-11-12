package com.mcpscanner.ui.engine;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.access.InvokedTools;
import com.mcpscanner.access.WriteReplayConfirmations;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.ToolAnnotations;
import com.mcpscanner.testutil.ManualReadScheduler;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.ui.widgets.UiReads;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmedMcpOnlyScanTest {

    private static final McpToolDefinition READ_ONLY = new McpToolDefinition("read_notes", "", "{}", List.of(),
            new ToolAnnotations(null, true, null, null, null));

    private final List<List<McpToolDefinition>> asked = new ArrayList<>();
    private final List<List<HttpRequestResponse>> scanned = new ArrayList<>();
    private final WriteReplayConfirmations confirmations = new WriteReplayConfirmations();
    private final McpEventLog eventLog = new McpEventLog(null);
    private boolean approves = true;
    private UiReads reads = ManualReadScheduler.immediateReads();

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void asksOnceForEveryToolTheSelectionCallsThenScansAndConfirmsTheWrites() {
        List<HttpRequestResponse> selection = List.of(toolCall("delete_account"), toolCall("rename_account"),
                toolCall("read_notes"));

        scan().accept(selection);

        assertThat(asked).singleElement().satisfies(tools -> assertThat(tools)
                .extracting(McpToolDefinition::name)
                .containsExactly("delete_account", "rename_account", "read_notes"));
        assertThat(scanned).containsExactly(selection);
        assertThat(confirmations.confirmed("delete_account")).isTrue();
        assertThat(confirmations.confirmed("rename_account")).isTrue();
        assertThat(confirmations.confirmed("read_notes")).isFalse();
    }

    @Test
    void readsTheSelectedRequestsOnTheSchedulerBeforeAsking() {
        ManualReadScheduler scheduler = new ManualReadScheduler();
        reads = scheduler.reads();

        scan().accept(List.of(toolCall("delete_account")));

        assertThat(asked).isEmpty();
        assertThat(scanned).isEmpty();
        scheduler.runNext();
        assertThat(asked).singleElement().satisfies(tools -> assertThat(tools)
                .extracting(McpToolDefinition::name).containsExactly("delete_account"));
        assertThat(scanned).hasSize(1);
    }

    @Test
    void declinedConfirmationScansNothingConfirmsNothingAndSaysSo() {
        approves = false;

        scan().accept(List.of(toolCall("delete_account")));

        assertThat(scanned).isEmpty();
        assertThat(confirmations.confirmed("delete_account")).isFalse();
        assertThat(eventLog.snapshot()).extracting(McpEventLog.LogEntry::message)
                .containsExactly("MCP-only scan cancelled at the unsafe-tool confirmation");
    }

    private ConfirmedMcpOnlyScan scan() {
        InvokedTools invokedTools = new InvokedTools(name -> name.equals(READ_ONLY.name())
                ? Optional.of(READ_ONLY) : Optional.empty());
        return new ConfirmedMcpOnlyScan(invokedTools, reads, tools -> {
            asked.add(tools);
            return approves;
        }, confirmations::confirm, eventLog, scanned::add);
    }

    private static HttpRequestResponse toolCall(String tool) {
        return HttpRequestResponse.httpRequestResponse(HttpRequest.httpRequestFromUrl("https://mcp.example.test/mcp")
                .withMethod("POST").withBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"" + tool + "\",\"arguments\":{}}}"), null);
    }
}
