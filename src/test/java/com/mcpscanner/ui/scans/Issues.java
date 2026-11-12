package com.mcpscanner.ui.scans;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueDefinition;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class Issues {

    private Issues() {
    }

    static AuditIssue issue(String name, AuditIssueSeverity severity) {
        return issue(name, severity, "<p>detail of " + name + "</p>", List.of());
    }

    static AuditIssue issue(String name, AuditIssueSeverity severity, String detail,
                            List<HttpRequestResponse> evidence) {
        AuditIssue issue = mock(AuditIssue.class);
        AuditIssueDefinition definition = mock(AuditIssueDefinition.class);
        when(definition.background()).thenReturn("<p>background of " + name + "</p>");
        when(issue.name()).thenReturn(name);
        when(issue.severity()).thenReturn(severity);
        when(issue.confidence()).thenReturn(AuditIssueConfidence.FIRM);
        when(issue.baseUrl()).thenReturn("https://mcp.example/mcp");
        when(issue.detail()).thenReturn(detail);
        when(issue.remediation()).thenReturn("<p>fix " + name + "</p>");
        when(issue.definition()).thenReturn(definition);
        when(issue.requestResponses()).thenReturn(evidence);
        return issue;
    }

    static HttpRequestResponse exchange(HttpRequest request, HttpResponse response) {
        HttpRequestResponse exchange = mock(HttpRequestResponse.class);
        when(exchange.request()).thenReturn(request);
        when(exchange.response()).thenReturn(response);
        when(exchange.hasResponse()).thenReturn(response != null);
        return exchange;
    }
}
