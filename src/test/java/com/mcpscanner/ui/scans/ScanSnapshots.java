package com.mcpscanner.ui.scans;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.engine.RunningInvocation;
import com.mcpscanner.engine.ScanError;
import com.mcpscanner.engine.ScanErrors;
import com.mcpscanner.engine.ScanFindings;
import com.mcpscanner.engine.ScanLabel;
import com.mcpscanner.engine.ScanProgress;
import com.mcpscanner.engine.ScanSnapshot;
import com.mcpscanner.engine.ScanStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ScanSnapshots {

    static final Instant START = Instant.parse("2026-11-11T10:00:00Z");
    static final ScanLabel LABEL = new ScanLabel("Scan 14 requests", "mcp.example", "Streamable HTTP", "User 1", "http://mcp.example");

    private ScanSnapshots() {
    }

    static ScanSnapshot running(long id) {
        return snapshot(id, ScanStatus.RUNNING, new ScanProgress(10, 4, 0, 0, false, 0));
    }

    static ScanSnapshot finished(long id) {
        return new ScanSnapshot(id, LABEL, ScanStatus.FINISHED, START, Optional.of(START.plusSeconds(65)),
                new ScanProgress(10, 10, 0, 0, false, 0), 3, new ScanErrors(List.of(), 0), List.of(), noIssues());
    }

    static ScanSnapshot snapshot(long id, ScanStatus status, ScanProgress progress) {
        return new ScanSnapshot(id, LABEL, status, START, Optional.empty(), progress, 0,
                new ScanErrors(List.of(), 0), List.of(), noIssues());
    }

    static ScanSnapshot withIssues(long id, List<AuditIssue> issues) {
        return new ScanSnapshot(id, LABEL, ScanStatus.RUNNING, START, Optional.empty(),
                new ScanProgress(10, 4, 0, issues.size(), false, 0), 0, new ScanErrors(List.of(), 0), List.of(),
                new ScanFindings(issues, issues.size(), Map.of()));
    }

    static ScanSnapshot withFindings(long id, ScanFindings findings) {
        return new ScanSnapshot(id, LABEL, ScanStatus.RUNNING, START, Optional.empty(),
                new ScanProgress(10, 4, 0, findings.total(), false, 0), 0, new ScanErrors(List.of(), 0), List.of(),
                findings);
    }

    static ScanSnapshot withErrors(long id, List<ScanError> errors) {
        return new ScanSnapshot(id, LABEL, ScanStatus.RUNNING, START, Optional.empty(),
                new ScanProgress(10, 4, 0, 0, false, 0), 0, new ScanErrors(errors, errors.size()), List.of(),
                noIssues());
    }

    static ScanSnapshot withNowRunning(long id, List<RunningInvocation> nowRunning) {
        return new ScanSnapshot(id, LABEL, ScanStatus.RUNNING, START, Optional.empty(),
                new ScanProgress(10, 4, 0, 0, false, 0), 0, new ScanErrors(List.of(), 0), nowRunning, noIssues());
    }

    static ScanSnapshot finishedWatching(long id, int watchedPayloads) {
        return new ScanSnapshot(id, LABEL, ScanStatus.FINISHED, START, Optional.of(START.plusSeconds(65)),
                new ScanProgress(10, 10, 0, 0, false, watchedPayloads), 3, new ScanErrors(List.of(), 0), List.of(),
                noIssues());
    }

    static ScanFindings findings(int total, Map<AuditIssueSeverity, Integer> bySeverity) {
        return new ScanFindings(List.of(), total, bySeverity);
    }

    private static ScanFindings noIssues() {
        return new ScanFindings(List.of(), 0, Map.of());
    }
}
