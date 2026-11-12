package com.mcpscanner.ui.scans;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.ui.widgets.SeverityOrder;

import java.util.Comparator;

final class IssueOrder {

    static final Comparator<AuditIssue> MOST_SEVERE_FIRST = Comparator
            .comparing(AuditIssue::severity, SeverityOrder.MOST_SEVERE_FIRST)
            .thenComparing(AuditIssue::confidence, SeverityOrder.MOST_CONFIDENT_FIRST)
            .thenComparing(issue -> String.valueOf(issue.name()));

    private IssueOrder() {
    }
}
