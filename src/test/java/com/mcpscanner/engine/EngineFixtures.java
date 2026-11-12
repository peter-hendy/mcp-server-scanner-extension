package com.mcpscanner.engine;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.issues.AuditIssue;

import com.mcpscanner.checks.registry.IssueDestination;
import com.mcpscanner.logging.McpEventLog;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class EngineFixtures {

    static final Duration BOUND = Duration.ofSeconds(5);
    static EngineEnvironment environment(McpEventLog eventLog) {
        return new EngineEnvironment(eventLog, Clock.systemUTC(), ScanInstrumentation.NONE, HttpRequest::url,
                scanId -> 0);
    }

    static final ScanLabel LABEL = new ScanLabel("Scan 1 request", "mcp.example", "HTTP 2026-07-28", "User 1", "http://mcp.example");

    private EngineFixtures() {
    }

    static IssueDestination siteMapOf(McpScanEngine engine) {
        return (check, issues, scan) -> engine.addIssues(check, issues,
                scan.map(context -> new ScanCredit(context.scanId(), context.testedRequest())));
    }

    static HttpRequestResponse answeredBase(String url) {
        return base(url, true);
    }

    static HttpRequestResponse unansweredBase(String url) {
        return base(url, false);
    }

    static List<AuditInsertionPoint> insertionPoints(int count) {
        return IntStream.range(0, count).mapToObj(index -> mock(AuditInsertionPoint.class)).toList();
    }

    static AuditIssue issue(String name, String baseUrl) {
        AuditIssue issue = mock(AuditIssue.class);
        when(issue.name()).thenReturn(name);
        when(issue.baseUrl()).thenReturn(baseUrl);
        return issue;
    }

    private static HttpRequestResponse base(String url, boolean answered) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.url()).thenReturn(url);
        HttpRequestResponse base = mock(HttpRequestResponse.class);
        when(base.request()).thenReturn(request);
        when(base.hasResponse()).thenReturn(answered);
        return base;
    }
}
