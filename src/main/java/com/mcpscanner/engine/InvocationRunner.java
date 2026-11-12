package com.mcpscanner.engine;

import burp.api.montoya.scanner.AuditResult;
import burp.api.montoya.scanner.audit.issues.AuditIssue;

import java.util.List;
import java.util.Optional;

final class InvocationRunner {

    private final SiteMapIssueSink issues;
    private final InvocationIssueTally tally;
    private final EngineLog log;

    InvocationRunner(SiteMapIssueSink issues, InvocationIssueTally tally, EngineLog log) {
        this.issues = issues;
        this.tally = tally;
        this.log = log;
    }

    List<AuditIssue> run(ScanRun run, CheckInvocation invocation) {
        EngineScan scan = run.scan();
        RunningInvocation running = invocation.running();
        try {
            scan.started(running);
            return run.instrumented().run(invocation.requestName(), () -> report(run, invocation));
        } catch (RuntimeException failure) {
            recordFailure(scan, invocation, FailureDescription.of(failure));
            return List.of();
        } finally {
            scan.ended(running);
        }
    }

    private List<AuditIssue> report(ScanRun run, CheckInvocation invocation) {
        return tally.collecting(() -> issues.report(invocation.check()::consolidateIssues,
                issuesOf(invocation.run(run.instrumented().http()))));
    }

    private void recordFailure(EngineScan scan, CheckInvocation invocation, String reason) {
        String check = invocation.check().checkName();
        log.debug("check " + check + " failed on " + invocation.target() + ": " + reason);
        scan.failed(Optional.of(check), invocation.target(), invocation.field(), reason);
    }

    private static List<AuditIssue> issuesOf(AuditResult result) {
        return result == null ? List.of() : result.auditIssues();
    }
}
