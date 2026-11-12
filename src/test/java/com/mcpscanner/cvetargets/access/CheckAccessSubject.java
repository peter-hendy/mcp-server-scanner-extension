package com.mcpscanner.cvetargets.access;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.api.CheckDescriptor;
import com.mcpscanner.checks.api.CheckKind;
import com.mcpscanner.checks.api.RequestCheck;

import java.util.List;

public final class CheckAccessSubject implements RequestCheck {

    private final CheckDescriptor descriptor;

    public CheckAccessSubject(String targetSetId) {
        this.descriptor = new CheckDescriptor(targetSetId, "Check access",
                "Mines each user's IDs, then runs Check access over the arm's inventory.", CheckKind.REQUEST,
                AuditIssueSeverity.HIGH, AuditIssueConfidence.FIRM, true, List.of());
    }

    @Override
    public CheckDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public List<AuditIssue> audit(HttpRequestResponse baseRequestResponse, Http http) {
        throw new UnsupportedOperationException("Check access runs through CheckAccessRunner, not as a scan check");
    }
}
