package com.mcpscanner.checks.catalog.sessionlifecycle;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.checks.api.SkipReason;

import java.util.Optional;
import java.util.function.Supplier;

final class GuardedArm {

    private GuardedArm() {
    }

    static Optional<AuditIssue> run(SkipLog test, ProbeTraffic traffic, Supplier<Optional<AuditIssue>> arm) {
        if (traffic.throttled()) {
            test.notRun(SkipReason.rateLimited());
            return Optional.empty();
        }
        try {
            return arm.get();
        } catch (RuntimeException failed) {
            test.failed(failed);
            return Optional.empty();
        }
    }
}
