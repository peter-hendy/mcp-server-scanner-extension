package com.mcpscanner.checks.catalog.outboundrequest;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.checks.issue.IssueRating;

record OutboundFinding(AuditIssue issue, InteractionStrength strength) {

    IssueRating rating() {
        return IssueRating.of(issue);
    }

    boolean settles() {
        return strength == InteractionStrength.HTTP_REQUEST;
    }

    boolean isStrongerThan(OutboundFinding other) {
        return rating().isStrongerThan(other.rating());
    }
}
