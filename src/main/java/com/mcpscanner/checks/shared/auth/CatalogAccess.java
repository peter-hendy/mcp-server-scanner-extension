package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.issue.IssueRating;

public enum CatalogAccess {
    ALTERNATE_ROUTE(new IssueRating(AuditIssueSeverity.LOW, AuditIssueConfidence.FIRM)),
    SESSION_CREDENTIAL(new IssueRating(AuditIssueSeverity.LOW, AuditIssueConfidence.FIRM));

    private final IssueRating rating;

    CatalogAccess(IssueRating rating) {
        this.rating = rating;
    }

    public IssueRating rating() {
        return rating;
    }
}
