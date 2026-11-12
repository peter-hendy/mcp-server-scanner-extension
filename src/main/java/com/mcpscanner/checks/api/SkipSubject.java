package com.mcpscanner.checks.api;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import com.mcpscanner.mcp.McpRequestNames;

public final class SkipSubject {

    private SkipSubject() {
    }

    public static String of(HttpRequestResponse base) {
        return base.request() == null ? "a base request without a request" : of(base.request());
    }

    public static String of(HttpRequest request) {
        return new McpRequestNames().nameOf(request) + " at " + request.url();
    }

    public static String field(HttpRequestResponse base, AuditInsertionPoint insertionPoint) {
        return "field " + insertionPoint.name() + " of " + of(base);
    }
}
