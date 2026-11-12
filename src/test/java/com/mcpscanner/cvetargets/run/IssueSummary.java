package com.mcpscanner.cvetargets.run;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;

public record IssueSummary(String name, AuditIssueSeverity severity, AuditIssueConfidence confidence, String baseUrl,
                           String detail) {

    public static IssueSummary of(AuditIssue issue) {
        return new IssueSummary(issue.name(), issue.severity(), issue.confidence(), issue.baseUrl(), issue.detail());
    }
}
