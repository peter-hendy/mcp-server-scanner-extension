package com.mcpscanner.checks.api;

import burp.api.montoya.scanner.audit.issues.AuditIssue;

import java.util.List;
import java.util.Objects;

public record HostTrialResult(List<AuditIssue> issues, HostSettlement settlement) {

    public HostTrialResult {
        issues = List.copyOf(issues);
        Objects.requireNonNull(settlement, "settlement must not be null");
    }

    public static HostTrialResult settled(List<AuditIssue> issues) {
        return new HostTrialResult(issues, HostSettlement.SETTLED);
    }
}
