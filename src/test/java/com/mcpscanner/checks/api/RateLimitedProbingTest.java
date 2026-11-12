package com.mcpscanner.checks.api;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitedProbingTest {

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    private final McpEventLog eventLog = new McpEventLog(null);
    private final RateLimitedProbing probing = new RateLimitedProbing(new CheckLog(eventLog, "demo"));
    private final AuditIssue issue = mock(AuditIssue.class);

    @Test
    void withholdsEveryProbeAfterTheFirstHttp429KeepsWhatWasFoundAndLogsWhy() {
        Http http = answering(429);
        List<HttpRequestResponse> replies = new ArrayList<>();

        List<AuditIssue> issues = probing.run("field q", http, guarded -> {
            for (int payload = 0; payload < 5; payload++) {
                replies.add(guarded.sendRequest(mock(HttpRequest.class)));
            }
            return List.of(issue);
        });

        assertThat(issues).containsExactly(issue);
        verify(http, times(1)).sendRequest(any(HttpRequest.class));
        assertThat(replies.subList(1, replies.size())).allSatisfy(reply -> assertThat(reply.response()).isNull());
        assertThat(eventLog.snapshot()).extracting(McpEventLog.LogEntry::message).containsExactly(
                "Scan check: demo — stopped field q (rate limited: the server answered HTTP 429)");
    }

    @Test
    void passesOtherRepliesThroughUntouched() {
        Http http = answering(200);

        List<AuditIssue> issues = probing.run("field q", http, guarded -> {
            guarded.sendRequest(mock(HttpRequest.class));
            return List.of(issue);
        });

        assertThat(issues).containsExactly(issue);
        assertThat(eventLog.snapshot()).isEmpty();
    }

    private static Http answering(int status) {
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn((short) status);
        HttpRequestResponse exchange = mock(HttpRequestResponse.class);
        when(exchange.response()).thenReturn(response);
        Http http = mock(Http.class);
        when(http.sendRequest(any(HttpRequest.class))).thenReturn(exchange);
        return http;
    }
}
