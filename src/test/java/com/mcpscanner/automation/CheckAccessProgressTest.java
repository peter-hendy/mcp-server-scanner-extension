package com.mcpscanner.automation;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.access.AccessRunSummary;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CheckAccessProgressTest {

    private final CheckAccessProgress progress = new CheckAccessProgress();

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void hasNoStatusBeforeTheFirstLaunch() {
        assertThat(progress.status()).isEmpty();
        assertThat(progress.active()).isFalse();
    }

    @Test
    void movesFromDiscoveringThroughMiningToRunningWithTheRowsItChecks() {
        progress.begin();
        assertThat(phase()).isEqualTo(CheckAccessPhase.DISCOVERING);

        progress.mining();
        assertThat(phase()).isEqualTo(CheckAccessPhase.MINING);

        progress.running(3);
        progress.progress(1, 3);
        CheckAccessStatus running = progress.status().orElseThrow();
        assertThat(running.status()).isEqualTo(CheckAccessPhase.RUNNING);
        assertThat(running.checked()).isEqualTo(1);
        assertThat(running.total()).isEqualTo(3);
        assertThat(progress.active()).isTrue();
    }

    @Test
    void aRunThatCheckedEveryItemFinishes() {
        progress.begin();
        progress.running(2);

        progress.finished(new AccessRunSummary(2, 2, 1, false));

        CheckAccessStatus status = progress.status().orElseThrow();
        assertThat(status.status()).isEqualTo(CheckAccessPhase.FINISHED);
        assertThat(status.checked()).isEqualTo(2);
        assertThat(status.reason()).isNull();
        assertThat(progress.active()).isFalse();
    }

    @Test
    void aCancelledRunIsCancelled() {
        progress.begin();

        progress.finished(new AccessRunSummary(1, 2, 0, true));

        assertThat(phase()).isEqualTo(CheckAccessPhase.CANCELLED);
    }

    @Test
    void aRunEndedByAStopRuleIsStoppedWithItsReason() {
        progress.begin();

        progress.finished(new AccessRunSummary(2, 2, 0, false, Optional.of("the server answered HTTP 429")));

        CheckAccessStatus status = progress.status().orElseThrow();
        assertThat(status.status()).isEqualTo(CheckAccessPhase.STOPPED);
        assertThat(status.reason()).isEqualTo("the server answered HTTP 429");
    }

    @Test
    void aFailureEndsTheLaunchWithItsReason() {
        progress.begin();

        progress.failed("Mining failed: boom");

        CheckAccessStatus status = progress.status().orElseThrow();
        assertThat(status.status()).isEqualTo(CheckAccessPhase.FAILED);
        assertThat(status.reason()).isEqualTo("Mining failed: boom");
        assertThat(progress.active()).isFalse();
    }

    @Test
    void reportsTheIssuesCheckAccessFiledAsExtensionIssues() {
        progress.begin();

        progress.accept(issue("MCP operation result available to another principal"));

        assertThat(progress.status().orElseThrow().issues()).singleElement().satisfies(reported -> {
            assertThat(reported.name()).isEqualTo("MCP operation result available to another principal");
            assertThat(reported.severity()).isEqualTo("HIGH");
            assertThat(reported.confidence()).isEqualTo("FIRM");
            assertThat(reported.extensionGenerated()).isTrue();
        });
    }

    @Test
    void countsTheRequestsCheckAccessSentAndThoseTheTargetAnswered() {
        Http http = mock(Http.class);
        HttpRequestResponse ok = answered(200);
        HttpRequestResponse limited = answered(429);
        when(http.sendRequest(any(HttpRequest.class))).thenReturn(ok, limited);
        progress.begin();

        Http counted = progress.counted(http);
        counted.sendRequest(mock(HttpRequest.class));
        counted.sendRequest(mock(HttpRequest.class));

        CheckAccessStatus status = progress.status().orElseThrow();
        assertThat(status.requestsSent()).isEqualTo(2);
        assertThat(status.requestsReached()).isEqualTo(1);
    }

    @Test
    void aNewLaunchForgetsTheLastOnesIssuesAndCounts() {
        Http http = mock(Http.class);
        HttpRequestResponse ok = answered(200);
        when(http.sendRequest(any(HttpRequest.class))).thenReturn(ok);
        Http counted = progress.counted(http);
        progress.begin();
        progress.accept(issue("earlier"));
        counted.sendRequest(mock(HttpRequest.class));
        progress.finished(new AccessRunSummary(1, 1, 1, false));

        progress.begin();

        CheckAccessStatus status = progress.status().orElseThrow();
        assertThat(status.issues()).isEmpty();
        assertThat(status.requestsSent()).isZero();
        assertThat(status.checked()).isZero();
    }

    private CheckAccessPhase phase() {
        return progress.status().orElseThrow().status();
    }

    private static HttpRequestResponse answered(int status) {
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn((short) status);
        HttpRequestResponse exchange = mock(HttpRequestResponse.class);
        when(exchange.response()).thenReturn(response);
        return exchange;
    }

    private static AuditIssue issue(String name) {
        AuditIssue issue = mock(AuditIssue.class);
        when(issue.name()).thenReturn(name);
        when(issue.severity()).thenReturn(AuditIssueSeverity.HIGH);
        when(issue.confidence()).thenReturn(AuditIssueConfidence.FIRM);
        when(issue.baseUrl()).thenReturn("http://target/mcp");
        when(issue.detail()).thenReturn("detail");
        return issue;
    }
}
