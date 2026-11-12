package com.mcpscanner.ui.engine;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.access.InvokedTools;
import com.mcpscanner.access.WriteReplayApproval;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.ui.DestructiveScanPolicy;
import com.mcpscanner.ui.widgets.UiReads;

import java.util.List;
import java.util.function.Consumer;

public final class ConfirmedMcpOnlyScan implements Consumer<List<HttpRequestResponse>> {

    static final String CANCELLED = "MCP-only scan cancelled at the unsafe-tool confirmation";

    private final InvokedTools invokedTools;
    private final UiReads reads;
    private final WriteReplayApproval approval;
    private final Consumer<List<McpToolDefinition>> confirmedWrites;
    private final McpEventLog eventLog;
    private final Consumer<List<HttpRequestResponse>> scan;

    public ConfirmedMcpOnlyScan(InvokedTools invokedTools, UiReads reads, WriteReplayApproval approval,
                                Consumer<List<McpToolDefinition>> confirmedWrites, McpEventLog eventLog,
                                Consumer<List<HttpRequestResponse>> scan) {
        this.invokedTools = invokedTools;
        this.reads = reads;
        this.approval = approval;
        this.confirmedWrites = confirmedWrites;
        this.eventLog = eventLog;
        this.scan = scan;
    }

    @Override
    public void accept(List<HttpRequestResponse> targets) {
        reads.compute(() -> invokedTools.in(targets), tools -> confirmThenScan(targets, tools));
    }

    private void confirmThenScan(List<HttpRequestResponse> targets, List<McpToolDefinition> tools) {
        if (!approval.approve(tools)) {
            eventLog.info(CANCELLED);
            return;
        }
        confirmedWrites.accept(DestructiveScanPolicy.nonReadOnly(tools));
        scan.accept(targets);
    }
}
