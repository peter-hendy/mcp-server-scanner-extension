package com.mcpscanner.engine;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record ScanFindings(List<AuditIssue> kept, int total, Map<AuditIssueSeverity, Integer> bySeverity,
                           Map<AuditIssue, String> testedRequests) {

    public ScanFindings(List<AuditIssue> kept, int total, Map<AuditIssueSeverity, Integer> bySeverity) {
        this(kept, total, bySeverity, Map.of());
    }

    public int count(AuditIssueSeverity severity) {
        return bySeverity.getOrDefault(severity, 0);
    }

    public Optional<String> testedRequest(AuditIssue issue) {
        return Optional.ofNullable(testedRequests.get(issue));
    }
}
