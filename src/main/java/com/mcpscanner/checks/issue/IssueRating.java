package com.mcpscanner.checks.issue;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;

import java.util.Comparator;

public record IssueRating(AuditIssueSeverity severity, AuditIssueConfidence confidence) {

    private static final Comparator<IssueRating> WEAKEST_FIRST = Comparator
            .comparing(IssueRating::severity, Comparator.reverseOrder())
            .thenComparing(IssueRating::confidence, Comparator.reverseOrder());

    public static IssueRating of(AuditIssue issue) {
        return new IssueRating(issue.severity(), issue.confidence());
    }

    public boolean isStrongerThan(IssueRating other) {
        return WEAKEST_FIRST.compare(this, other) > 0;
    }
}
