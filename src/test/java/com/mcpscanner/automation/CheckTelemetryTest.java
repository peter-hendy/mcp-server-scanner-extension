package com.mcpscanner.automation;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.http.OutsideBurpSends;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CheckTelemetryTest {

    private final ProbeCounter total = ProbeCounter.throughTheExtensionProxy();
    private final CheckTelemetry telemetry = new CheckTelemetry(total, ProbeCounter::throughTheExtensionProxy);
    private final Http delegate = mock(Http.class);

    @Test
    void countsEachCheckProbeUnderItsCheckAndInTheMarkedTotal() {
        HttpRequest probe = mock(HttpRequest.class);
        HttpRequest marked = mock(HttpRequest.class);
        when(probe.withAddedHeader(CheckProbeMarker.HEADER, "1")).thenReturn(marked);
        HttpRequestResponse answered = exchange(200);
        HttpRequestResponse rateLimited = exchange(429);
        when(delegate.sendRequest(marked)).thenReturn(answered, rateLimited, answered);

        telemetry.probeHttp("file-traversal", delegate).sendRequest(probe);
        telemetry.probeHttp("file-traversal", delegate).sendRequest(probe);
        telemetry.probeHttp("outbound-request", delegate).sendRequest(probe);

        assertThat(telemetry.probesByCheck()).isEqualTo(Map.of(
                "file-traversal", new CheckProbes(2, 1),
                "outbound-request", new CheckProbes(1, 1)));
        assertThat(total.sent()).isEqualTo(3);
        assertThat(total.reached()).isEqualTo(2);
    }

    @Test
    void countsACheckProbeSentOutsideBurpUnderItsCheckAndInTheTotal() {
        OutsideBurpSends.report(telemetry.probeHttp("session-lifecycle", delegate), exchange(200));

        assertThat(telemetry.probesByCheck()).isEqualTo(Map.of("session-lifecycle", new CheckProbes(1, 1)));
        assertThat(telemetry.sent()).isEqualTo(1);
        assertThat(telemetry.reached()).isEqualTo(1);
    }

    @Test
    void attributesAnIssueNameToTheOneCheckThatReportedIt() {
        telemetry.reported("file-traversal", List.of(issue("MCP arbitrary host file read")));
        telemetry.reported("outbound-request", List.of(issue("MCP outbound request")));

        assertThat(telemetry.checksReporting("MCP arbitrary host file read")).containsExactly("file-traversal");
        assertThat(telemetry.checksReporting("MCP outbound request")).containsExactly("outbound-request");
        assertThat(telemetry.checksReporting("File path traversal")).isEmpty();
    }

    @Test
    void namesEveryCheckThatReportedAnIssueNameSoACollisionStaysVisible() {
        telemetry.reported("outbound-request", List.of(issue("Shared name")));
        telemetry.reported("file-traversal", List.of(issue("Shared name")));

        assertThat(telemetry.checksReporting("Shared name")).containsExactly("file-traversal", "outbound-request");
    }

    @Test
    void resetForgetsEveryProbeAndEveryAttributionSoEachAuditAttributesOnlyItsOwnIssues() {
        HttpRequestResponse answered = exchange(200);
        when(delegate.sendRequest(any(HttpRequest.class))).thenReturn(answered);
        telemetry.probeHttp("file-traversal", delegate).sendRequest(mock(HttpRequest.class));
        telemetry.reported("file-traversal", List.of(issue("MCP arbitrary host file read")));

        telemetry.reset();

        assertThat(telemetry.probesByCheck()).isEmpty();
        assertThat(telemetry.sent()).isZero();
        assertThat(telemetry.reached()).isZero();
        assertThat(telemetry.checksReporting("MCP arbitrary host file read")).isEmpty();
    }

    private static AuditIssue issue(String name) {
        AuditIssue issue = mock(AuditIssue.class);
        when(issue.name()).thenReturn(name);
        return issue;
    }

    private static HttpRequestResponse exchange(int status) {
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn((short) status);
        when(response.bodyToString()).thenReturn("{}");
        HttpRequestResponse exchange = mock(HttpRequestResponse.class);
        when(exchange.response()).thenReturn(response);
        return exchange;
    }
}
