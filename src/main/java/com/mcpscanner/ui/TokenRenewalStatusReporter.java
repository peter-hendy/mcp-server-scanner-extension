package com.mcpscanner.ui;

import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.ui.state.ConnectionStatus;

import java.util.function.Consumer;

final class TokenRenewalStatusReporter implements TokenRenewal.Reporter {

    private final McpEventLog eventLog;
    private final Consumer<ConnectionStatus> status;

    TokenRenewalStatusReporter(McpEventLog eventLog, Consumer<ConnectionStatus> status) {
        this.eventLog = eventLog;
        this.status = status;
    }

    @Override
    public void renewed() {
        eventLog.info("Access token refreshed");
        status.accept(ConnectionStatus.tokenRefreshed());
    }

    @Override
    public void failed(String reason) {
        eventLog.error("Token refresh failed: " + reason);
        status.accept(ConnectionStatus.tokenRefreshFailed(reason));
    }
}
