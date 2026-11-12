package com.mcpscanner.checks.api;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.issues.AuditIssue;

import java.util.List;

public non-sealed interface InsertionPointCheck extends McpCheck {

    List<AuditIssue> audit(HttpRequestResponse baseRequestResponse, AuditInsertionPoint insertionPoint, Http http);

    @Override
    default <R> R accept(CheckVisitor<R> visitor) {
        return visitor.visitInsertionPointCheck(this);
    }
}
