package com.mcpscanner.checks.api;

public interface AccessLedger extends AccessExpectations, AccessOutcomeSink, ReplayPermission {

    static AccessLedger noop() {
        return NoopAccessLedger.INSTANCE;
    }
}
