package com.mcpscanner.checks.catalog.fixtureresend;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.api.CheckDescriptor;
import com.mcpscanner.checks.api.CheckKind;
import com.mcpscanner.checks.api.RequestCheck;

import java.util.List;

public final class ResendSucceedsCheck implements RequestCheck {

    static final CheckDescriptor DESCRIPTOR = new CheckDescriptor(
            "fixture-resend-succeeds", "Fixture resend succeeds", "Reports a request that can be replayed.",
            CheckKind.REQUEST, AuditIssueSeverity.LOW, AuditIssueConfidence.FIRM,
            true, List.of("https://example.test/fixture"));

    @Override
    public CheckDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public List<AuditIssue> audit(HttpRequestResponse baseRequestResponse, Http http) {
        HttpRequestResponse replay = http.sendRequest(baseRequestResponse.request());
        if (replay.response() == null || replay.response().statusCode() != 200) {
            return List.of();
        }
        return List.of(AuditIssue.auditIssue(DESCRIPTOR.issueName(), "replayed", "",
                baseRequestResponse.request().url(), DESCRIPTOR.headlineSeverity(), DESCRIPTOR.headlineConfidence(),
                "", "", DESCRIPTOR.headlineSeverity(), replay));
    }
}
