package com.mcpscanner.checks.api;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.checks.shared.collaborator.CollaboratorWatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SettlingLateIssues implements LateIssueSink, LateIssueSettlement {

    private final CollaboratorWatch watch;
    private final Map<McpCheck, List<AuditIssue>> collected = new HashMap<>();

    public SettlingLateIssues(CollaboratorWatch watch) {
        this.watch = watch;
    }

    @Override
    public synchronized void report(McpCheck check, List<AuditIssue> issues) {
        collected.computeIfAbsent(check, ignored -> new ArrayList<>()).addAll(issues);
    }

    @Override
    public List<AuditIssue> settle(McpCheck check) {
        watch.awaitSettled();
        return drain(check);
    }

    private synchronized List<AuditIssue> drain(McpCheck check) {
        List<AuditIssue> drained = collected.remove(check);
        return drained == null ? List.of() : List.copyOf(drained);
    }
}
