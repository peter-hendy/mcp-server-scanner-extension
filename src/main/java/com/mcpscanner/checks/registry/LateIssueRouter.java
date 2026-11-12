package com.mcpscanner.checks.registry;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.checks.api.LateIssueSink;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.checks.shared.scancontext.CurrentScan;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class LateIssueRouter implements LateIssueSink {

    private final IssueDestination destination;
    private final AtomicReference<ScanCheckRegistry> registry = new AtomicReference<>();

    public LateIssueRouter(IssueDestination destination) {
        this.destination = Objects.requireNonNull(destination, "destination must not be null");
    }

    public void routeThrough(ScanCheckRegistry checks) {
        registry.set(Objects.requireNonNull(checks, "checks must not be null"));
    }

    @Override
    public void report(McpCheck check, List<AuditIssue> issues) {
        Optional.ofNullable(registry.get())
                .flatMap(checks -> checks.adapterRunning(check))
                .ifPresent(adapter -> destination.add(adapter, adapter.lateIssues(issues), CurrentScan.get()));
    }
}
