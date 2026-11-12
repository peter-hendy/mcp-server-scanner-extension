package com.mcpscanner.checks.catalog.sessionbinding;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.crossidentity.IdentityIssue;
import com.mcpscanner.checks.api.crossidentity.IdentityObservation;
import com.mcpscanner.checks.api.crossidentity.OperationSeed;
import com.mcpscanner.checks.api.crossidentity.ReplayedOperation;
import com.mcpscanner.checks.api.crossidentity.ResultAvailable;

import java.util.List;

final class SessionBindingIssues {

    static final String REBOUND_NAME = "MCP session rebound to another principal's credential";

    private static final String BOUND_RESULT_FAMILY = "session";
    private static final String REBOUND_FAMILY = "session-rebind";
    private static final String REBOUND_BACKGROUND = "A second identity's credential sent once on an authenticated "
            + "MCP session replaced the principal bound to that session, so later requests on the session ran as "
            + "the second identity.";

    private SessionBindingIssues() {
    }

    static IdentityIssue ownerResult(Principal principal, boolean firm, String text,
                                     List<HttpRequestResponse> evidence) {
        AuditIssueConfidence confidence = firm ? AuditIssueConfidence.FIRM : AuditIssueConfidence.TENTATIVE;
        return new IdentityIssue(ResultAvailable.ISSUE_NAME, ResultAvailable.severity(confidence), confidence,
                ResultAvailable.BACKGROUND, ResultAvailable.strength(confidence),
                new IdentityObservation(principal, BOUND_RESULT_FAMILY, text, evidence));
    }

    static IdentityIssue rebound(Principal principal, String text, List<HttpRequestResponse> evidence) {
        return new IdentityIssue(REBOUND_NAME, AuditIssueSeverity.MEDIUM, AuditIssueConfidence.TENTATIVE,
                REBOUND_BACKGROUND, "", new IdentityObservation(principal, REBOUND_FAMILY, text, evidence));
    }

    static AuditIssue render(OperationSeed seed, ReplayedOperation replayed, IdentityIssue issue) {
        return issue.render(seed, replayed, SessionBindingDescriptor.DESCRIPTOR);
    }
}
