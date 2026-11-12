package com.mcpscanner.checks.catalog.fixturereflection;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.api.CheckDescriptor;
import com.mcpscanner.checks.api.CheckKind;
import com.mcpscanner.checks.api.InsertionPointCheck;

import java.util.List;

public final class ReflectedMarkerCheck implements InsertionPointCheck {

    public static final String MARKER = "fixture-marker-7f3a";
    static final CheckDescriptor DESCRIPTOR = new CheckDescriptor(
            "fixture-reflected-marker", "Fixture reflected marker", "Reports a reflected marker.",
            CheckKind.INSERTION_POINT, AuditIssueSeverity.INFORMATION, AuditIssueConfidence.CERTAIN,
            true, List.of("https://example.test/fixture"));

    @Override
    public CheckDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public List<AuditIssue> audit(HttpRequestResponse baseRequestResponse, AuditInsertionPoint insertionPoint, Http http) {
        HttpRequestResponse probe = http.sendRequest(
                insertionPoint.buildHttpRequestWithPayload(ByteArray.byteArray(MARKER)));
        if (probe.response() == null || !probe.response().bodyToString().contains(MARKER)) {
            return List.of();
        }
        return List.of(AuditIssue.auditIssue(DESCRIPTOR.issueName(), insertionPoint.name(), "",
                baseRequestResponse.request().url(), DESCRIPTOR.headlineSeverity(), DESCRIPTOR.headlineConfidence(),
                "", "", DESCRIPTOR.headlineSeverity(), probe));
    }
}
