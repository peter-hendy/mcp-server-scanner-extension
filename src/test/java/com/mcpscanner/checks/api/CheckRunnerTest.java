package com.mcpscanner.checks.api;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CheckRunnerTest {

    private final Http http = mock(Http.class);
    private final AuditIssue issue = mock(AuditIssue.class);
    private final HttpRequestResponse firstOnHostA = base("a.test");
    private final HttpRequestResponse secondOnHostA = base("a.test");
    private final HttpRequestResponse onHostB = base("b.test");
    private final AuditInsertionPoint pointOne = mock(AuditInsertionPoint.class);
    private final AuditInsertionPoint pointTwo = mock(AuditInsertionPoint.class);
    private final Map<HttpRequestResponse, List<AuditInsertionPoint>> insertionPoints = Map.of(
            firstOnHostA, List.of(pointOne, pointTwo),
            secondOnHostA, List.of(pointOne),
            onHostB, List.of());
    private final CheckRunner runner = new CheckRunner(http, insertionPoints::get);

    @Test
    void appendsTheIssuesTheCheckReportedLateOnceTheyHaveSettled() {
        AuditIssue late = mock(AuditIssue.class);
        RecordingChecks.Request check = new RecordingChecks.Request("req", List.of(issue));
        List<McpCheck> settled = new java.util.ArrayList<>();
        CheckRunner settling = new CheckRunner(http, insertionPoints::get, settledCheck -> {
            settled.add(settledCheck);
            return List.of(late);
        });

        List<AuditIssue> issues = settling.run(check, List.of(firstOnHostA));

        assertThat(issues).containsExactly(issue, late);
        assertThat(settled).containsExactly(check);
    }

    @Test
    void insertionPointCheckRunsOncePerInsertionPointOfEveryBaseRequest() {
        RecordingChecks.InsertionPoint check = new RecordingChecks.InsertionPoint("ip", List.of(issue));

        List<AuditIssue> issues = runner.run(check, List.of(firstOnHostA, secondOnHostA, onHostB));

        assertThat(check.calls).extracting(RecordingChecks.Call::base, RecordingChecks.Call::insertionPoint)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(firstOnHostA, pointOne),
                        org.assertj.core.groups.Tuple.tuple(firstOnHostA, pointTwo),
                        org.assertj.core.groups.Tuple.tuple(secondOnHostA, pointOne));
        assertThat(issues).hasSize(3);
    }

    @Test
    void requestCheckRunsOncePerBaseRequest() {
        RecordingChecks.Request check = new RecordingChecks.Request("req", List.of(issue));

        List<AuditIssue> issues = runner.run(check, List.of(firstOnHostA, secondOnHostA, onHostB));

        assertThat(check.calls).extracting(RecordingChecks.Call::base)
                .containsExactly(firstOnHostA, secondOnHostA, onHostB);
        assertThat(issues).containsExactly(issue, issue, issue);
    }

    @Test
    void hostCheckRunsOncePerDistinctHostUsingTheFirstRequestSeenForIt() {
        RecordingChecks.Host check = new RecordingChecks.Host("host", List.of(issue));

        List<AuditIssue> issues = runner.run(check, List.of(firstOnHostA, secondOnHostA, onHostB));

        assertThat(check.calls).extracting(RecordingChecks.Call::base).containsExactly(firstOnHostA, onHostB);
        assertThat(issues).hasSize(2);
    }

    @Test
    void hostCheckSkipsBaseRequestsItDoesNotApplyTo() {
        RecordingChecks.Host check = new RecordingChecks.Host("host", List.of(issue), base -> base != firstOnHostA);

        runner.run(check, List.of(firstOnHostA, secondOnHostA, onHostB));

        assertThat(check.calls).extracting(RecordingChecks.Call::base).containsExactly(secondOnHostA, onHostB);
    }

    @Test
    void everyKindReceivesTheRunnersHttp() {
        RecordingChecks.Request request = new RecordingChecks.Request("req", List.of());
        RecordingChecks.Host host = new RecordingChecks.Host("host", List.of());
        RecordingChecks.InsertionPoint insertionPoint = new RecordingChecks.InsertionPoint("ip", List.of());

        runner.run(request, List.of(firstOnHostA));
        runner.run(host, List.of(firstOnHostA));
        runner.run(insertionPoint, List.of(firstOnHostA));

        assertThat(request.calls).allMatch(call -> call.http() == http);
        assertThat(host.calls).allMatch(call -> call.http() == http);
        assertThat(insertionPoint.calls).allMatch(call -> call.http() == http);
    }

    @Test
    void noBaseRequestsMeansNoAuditAndNoIssues() {
        RecordingChecks.Host check = new RecordingChecks.Host("host", List.of(issue));

        assertThat(runner.run(check, List.of())).isEmpty();
        assertThat(check.calls).isEmpty();
    }

    @Test
    void rejectsMissingCollaborators() {
        assertThatThrownBy(() -> new CheckRunner(null, base -> List.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CheckRunner(http, null)).isInstanceOf(NullPointerException.class);
    }

    private static HttpRequestResponse base(String host) {
        HttpService service = HostKeyTest.service(host, 443, true);
        HttpRequestResponse base = mock(HttpRequestResponse.class);
        when(base.httpService()).thenReturn(service);
        return base;
    }
}
