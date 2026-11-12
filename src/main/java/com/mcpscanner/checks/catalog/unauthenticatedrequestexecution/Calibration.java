package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import com.mcpscanner.checks.issue.IssueRating;
import com.mcpscanner.checks.shared.auth.CatalogAccess;
import com.mcpscanner.checks.shared.probe.McpMethodClass;

import static burp.api.montoya.scanner.audit.issues.AuditIssueConfidence.FIRM;
import static burp.api.montoya.scanner.audit.issues.AuditIssueConfidence.TENTATIVE;
import static burp.api.montoya.scanner.audit.issues.AuditIssueSeverity.HIGH;
import static burp.api.montoya.scanner.audit.issues.AuditIssueSeverity.INFORMATION;
import static burp.api.montoya.scanner.audit.issues.AuditIssueSeverity.LOW;
import static burp.api.montoya.scanner.audit.issues.AuditIssueSeverity.MEDIUM;

final class Calibration {

    IssueRating rate(Finding finding) {
        McpMethodClass methodClass = finding.subject().methodClass();
        return switch (finding.kind()) {
            case ANONYMOUS_EXECUTION -> new IssueRating(LOW, TENTATIVE);
            case ANONYMOUS_EXECUTION_DESPITE_ENFORCEMENT -> new IssueRating(MEDIUM, FIRM);
            case ANONYMOUS_CATALOG -> new IssueRating(INFORMATION, FIRM);
            case INVALID_CREDENTIAL_ACCEPTED -> invalidCredential(methodClass);
            case AUTHENTICATION_BYPASS -> bypass(methodClass);
            case SESSION_CREDENTIAL -> sessionCredential(methodClass);
            case FORWARDED_CLIENT_ADDRESS_TRUSTED -> forwardedClient(methodClass);
            case ANONYMOUS_STREAM -> new IssueRating(MEDIUM, FIRM);
            case SESSION_STREAM -> new IssueRating(LOW, FIRM);
        };
    }

    private static IssueRating forwardedClient(McpMethodClass methodClass) {
        return methodClass == McpMethodClass.OPERATION ? new IssueRating(MEDIUM, FIRM) : new IssueRating(LOW, FIRM);
    }

    private static IssueRating bypass(McpMethodClass methodClass) {
        return switch (methodClass) {
            case OPERATION -> new IssueRating(HIGH, FIRM);
            case CATALOG -> CatalogAccess.ALTERNATE_ROUTE.rating();
            case PROTOCOL -> new IssueRating(LOW, TENTATIVE);
            case NOTIFICATION -> new IssueRating(INFORMATION, TENTATIVE);
        };
    }

    private static IssueRating invalidCredential(McpMethodClass methodClass) {
        return methodClass == McpMethodClass.OPERATION ? new IssueRating(MEDIUM, FIRM) : new IssueRating(INFORMATION, FIRM);
    }

    private static IssueRating sessionCredential(McpMethodClass methodClass) {
        return switch (methodClass) {
            case OPERATION -> new IssueRating(LOW, FIRM);
            case CATALOG -> CatalogAccess.SESSION_CREDENTIAL.rating();
            case PROTOCOL, NOTIFICATION -> new IssueRating(LOW, TENTATIVE);
        };
    }
}
