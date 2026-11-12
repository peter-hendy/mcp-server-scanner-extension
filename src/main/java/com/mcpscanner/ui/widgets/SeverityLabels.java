package com.mcpscanner.ui.widgets;

import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;

public final class SeverityLabels {

    private SeverityLabels() {
    }

    public static String of(AuditIssueSeverity severity) {
        if (severity == null) {
            return "";
        }
        return switch (severity) {
            case HIGH -> "High";
            case MEDIUM -> "Medium";
            case LOW -> "Low";
            case INFORMATION -> "Info";
            case FALSE_POSITIVE -> "False positive";
        };
    }

    public static String of(AuditIssueConfidence confidence) {
        if (confidence == null) {
            return "";
        }
        return switch (confidence) {
            case CERTAIN -> "Certain";
            case FIRM -> "Firm";
            case TENTATIVE -> "Tentative";
        };
    }
}
