package com.mcpscanner.cvetargets.manifest;

import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;

import java.util.List;

public final class SeverityRank {

    private static final List<AuditIssueSeverity> ASCENDING = List.of(
            AuditIssueSeverity.INFORMATION, AuditIssueSeverity.LOW, AuditIssueSeverity.MEDIUM, AuditIssueSeverity.HIGH);

    private SeverityRank() {
    }

    public static boolean isRanked(AuditIssueSeverity severity) {
        return ASCENDING.contains(severity);
    }

    public static boolean reaches(AuditIssueSeverity actual, AuditIssueSeverity floor) {
        return floor == null || isRanked(actual) && ASCENDING.indexOf(actual) >= ASCENDING.indexOf(floor);
    }
}
