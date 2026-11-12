package com.mcpscanner.scan;

import burp.api.montoya.scanner.audit.Audit;

import java.util.function.Consumer;

public record BurpAuditRun(Audit audit) implements RunningScan {

    @Override
    public ScanActivity activity() {
        return ScanActivity.burpAudit(audit.requestCount(), audit.issues().size());
    }

    @Override
    public boolean isFinished() {
        return false;
    }

    @Override
    public boolean outlivesConnection() {
        return true;
    }

    @Override
    public void cancel() {
        audit.delete();
    }

    @Override
    public void whenFinished(Consumer<ScanOutcome> listener) {
    }
}
