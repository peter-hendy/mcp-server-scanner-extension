package com.mcpscanner.checks.api.crossidentity;

import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;

public final class ResultAvailable {

    public static final String ISSUE_NAME = "MCP operation result available to another principal";
    public static final String BACKGROUND = "A captured MCP operation was replayed for a second identity, or for the "
            + "owner with coordinates naming another identity's object, and returned that protected result while "
            + "anonymous and absent-object controls did not.";

    private ResultAvailable() {
    }

    public static AuditIssueSeverity severity(AuditIssueConfidence confidence) {
        return proven(confidence) ? AuditIssueSeverity.HIGH : AuditIssueSeverity.MEDIUM;
    }

    public static String strength(AuditIssueConfidence confidence) {
        return proven(confidence) ? "The configured marker, absent from the request, appeared in that result."
                : "The responses do not establish ownership: review whether the result is meant to be shared.";
    }

    private static boolean proven(AuditIssueConfidence confidence) {
        return confidence != AuditIssueConfidence.TENTATIVE;
    }
}
