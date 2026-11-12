package com.mcpscanner.ui.widgets;

import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;

import java.util.Comparator;
import java.util.List;

public final class SeverityOrder {

    public static final List<AuditIssueSeverity> RATED = List.of(AuditIssueSeverity.HIGH,
            AuditIssueSeverity.MEDIUM, AuditIssueSeverity.LOW, AuditIssueSeverity.INFORMATION);
    public static final Comparator<AuditIssueSeverity> MOST_SEVERE_FIRST = Comparator.nullsLast(
            Comparator.comparing(SeverityOrder::severityRank));
    public static final Comparator<AuditIssueConfidence> MOST_CONFIDENT_FIRST = Comparator.nullsLast(
            Comparator.comparing(SeverityOrder::confidenceRank));

    private SeverityOrder() {
    }

    private static int severityRank(AuditIssueSeverity severity) {
        return switch (severity) {
            case HIGH -> 0;
            case MEDIUM -> 1;
            case LOW -> 2;
            case INFORMATION -> 3;
            case FALSE_POSITIVE -> 4;
        };
    }

    private static int confidenceRank(AuditIssueConfidence confidence) {
        return switch (confidence) {
            case CERTAIN -> 0;
            case FIRM -> 1;
            case TENTATIVE -> 2;
        };
    }
}
