package com.mcpscanner.checks.catalog.fixturebanner;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.api.CheckDescriptor;
import com.mcpscanner.checks.api.CheckKind;
import com.mcpscanner.checks.api.HostCheck;

import java.util.List;

public final class ServerBannerCheck implements HostCheck {

    static final CheckDescriptor DESCRIPTOR = new CheckDescriptor(
            "fixture-server-banner", "Fixture server banner", "Reports a host that names its server.",
            CheckKind.HOST, AuditIssueSeverity.INFORMATION, AuditIssueConfidence.CERTAIN,
            true, List.of("https://example.test/fixture"));

    @Override
    public CheckDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public boolean appliesTo(HttpRequestResponse hostBaseRequestResponse) {
        return hostBaseRequestResponse.response() != null;
    }

    @Override
    public String appliesOnlyTo() {
        return "answered requests";
    }

    @Override
    public List<AuditIssue> audit(HttpRequestResponse hostBaseRequestResponse, Http http) {
        HttpRequestResponse probe = http.sendRequest(hostBaseRequestResponse.request());
        if (probe.response() == null || probe.response().headerValue("X-Fixture-Server") == null) {
            return List.of();
        }
        return List.of(AuditIssue.auditIssue(DESCRIPTOR.issueName(), probe.response().headerValue("X-Fixture-Server"),
                "", hostBaseRequestResponse.request().url(), DESCRIPTOR.headlineSeverity(),
                DESCRIPTOR.headlineConfidence(), "", "", DESCRIPTOR.headlineSeverity(), probe));
    }
}
