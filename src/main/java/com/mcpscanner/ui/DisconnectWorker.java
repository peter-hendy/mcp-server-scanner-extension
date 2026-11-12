package com.mcpscanner.ui;

import com.mcpscanner.client.McpClientManager;

import javax.swing.SwingWorker;
import java.util.function.Consumer;

final class DisconnectWorker extends SwingWorker<Void, Void> {

    private final McpClientManager clientManager;
    private final Consumer<String> errorLog;
    private final Runnable afterDisconnect;

    DisconnectWorker(McpClientManager clientManager, Consumer<String> errorLog, Runnable afterDisconnect) {
        this.clientManager = clientManager;
        this.errorLog = errorLog;
        this.afterDisconnect = afterDisconnect;
    }

    @Override
    protected Void doInBackground() {
        clientManager.disconnect();
        return null;
    }

    @Override
    protected void done() {
        try {
            get();
        } catch (Exception ex) {
            errorLog.accept("Disconnect failed: " + Failures.reason(ex));
        }
        afterDisconnect.run();
    }
}
