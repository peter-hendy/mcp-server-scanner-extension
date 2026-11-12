package com.mcpscanner.checks.api;

enum NoopAccessLedger implements AccessLedger {
    INSTANCE;

    @Override
    public AccessExpectation expectationFor(AccessItemKey item) {
        return AccessExpectation.ANY_SIGNED_IN;
    }

    @Override
    public void record(AccessItemKey item, String principalId, AccessOutcome outcome) {
    }

    @Override
    public void recordAnonymous(AccessItemKey item, AccessOutcome outcome) {
    }

    @Override
    public boolean permitsReplay(AccessItemKey item) {
        return true;
    }
}
