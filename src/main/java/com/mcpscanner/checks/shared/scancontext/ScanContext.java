package com.mcpscanner.checks.shared.scancontext;

import java.util.Optional;
import java.util.function.BooleanSupplier;

public record ScanContext(long scanId, ScanNotices notices, Optional<String> testedRequest,
                          BooleanSupplier cancellation) {

    private static final BooleanSupplier NEVER_CANCELLED = () -> false;

    public ScanContext(long scanId) {
        this(scanId, NEVER_CANCELLED);
    }

    public ScanContext(long scanId, BooleanSupplier cancellation) {
        this(scanId, new ScanNotices(), Optional.empty(), cancellation);
    }

    public ScanContext forRequest(String request) {
        return new ScanContext(scanId, notices, Optional.of(request), cancellation);
    }

    public boolean cancelled() {
        return cancellation.getAsBoolean();
    }
}
