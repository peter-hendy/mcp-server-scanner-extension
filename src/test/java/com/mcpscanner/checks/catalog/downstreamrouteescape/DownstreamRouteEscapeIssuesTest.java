package com.mcpscanner.checks.catalog.downstreamrouteescape;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DownstreamRouteEscapeIssuesTest {

    private final DownstreamRouteEscapeIssues issues =
            new DownstreamRouteEscapeIssues(DownstreamRouteEscapeDescriptor.DESCRIPTOR);

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void issueNamesTheFieldTheValuesAndTheLimitsOfTheEvidence() {
        HttpRequestResponse base = mock(HttpRequestResponse.class);
        HttpRequest request = mock(HttpRequest.class);
        when(request.url()).thenReturn("http://target.test/mcp");
        when(request.bodyToString()).thenReturn("{\"method\":\"tools/call\",\"params\":{\"name\":\"get_item\"}}");
        when(base.request()).thenReturn(request);
        AuditInsertionPoint insertionPoint = mock(AuditInsertionPoint.class);
        when(insertionPoint.name()).thenReturn("MCP field: params.arguments.id");
        HttpRequestResponse probe = mock(HttpRequestResponse.class);
        RouteDifference difference = new RouteDifference(
                new RouteProbe(List.of("../", ".././"), List.of(".../", "..././"), List.of()), List.of(probe, probe));

        AuditIssue issue = issues.issueFor(base, insertionPoint, difference);

        assertThat(issue.name()).isEqualTo(DownstreamRouteEscapeDescriptor.DESCRIPTOR.issueName());
        assertThat(issue.severity()).isEqualTo(AuditIssueSeverity.INFORMATION);
        assertThat(issue.confidence()).isEqualTo(AuditIssueConfidence.TENTATIVE);
        assertThat(issue.detail()).contains("tools/call get_item").contains("MCP field: params.arguments.id")
                .contains("&quot;../&quot;").contains("&quot;.././&quot;")
                .contains("&quot;.../&quot;").contains("does not show");
        assertThat(issue.remediation()).isNotBlank();
        assertThat(issue.requestResponses()).containsExactly(base, probe, probe);
    }
}
