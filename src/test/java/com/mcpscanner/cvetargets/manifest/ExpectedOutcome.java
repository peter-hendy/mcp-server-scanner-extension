package com.mcpscanner.cvetargets.manifest;

import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;

public record ExpectedOutcome(
        Outcome outcome,
        String issueName,
        AuditIssueSeverity severity,
        AuditIssueConfidence confidence,
        AuditIssueSeverity minimumSeverity,
        String knownMiss,
        String knownFlaky) {

    public ExpectedOutcome(Outcome outcome, String issueName, AuditIssueSeverity severity,
                           AuditIssueConfidence confidence, AuditIssueSeverity minimumSeverity, String knownMiss) {
        this(outcome, issueName, severity, confidence, minimumSeverity, knownMiss, null);
    }

    public static ExpectedOutcome of(Outcome outcome) {
        return new ExpectedOutcome(outcome, null, null, null, null, null);
    }

    public boolean isKnownMiss() {
        return knownMiss != null;
    }

    public boolean isKnownFlaky() {
        return knownFlaky != null;
    }
}
