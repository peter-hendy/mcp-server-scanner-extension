package com.mcpscanner.checks.registry;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.checks.api.CheckDescriptor;
import com.mcpscanner.checks.api.CheckKind;
import com.mcpscanner.checks.api.RecordingChecks;
import com.mcpscanner.checks.api.RequestCheck;
import com.mcpscanner.checks.shared.scancontext.CurrentScan;
import com.mcpscanner.checks.shared.scancontext.ScanContext;
import com.mcpscanner.client.ProtocolHttpRequest;
import com.mcpscanner.client.ProtocolHttpResponse;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.FakeMcpServer;
import com.mcpscanner.testutil.ImmutableHttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RetryAfterInvocationTest {

    private final McpEventLog eventLog = new McpEventLog(null);
    private final AtomicInteger received = new AtomicInteger();

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void aCheckInvocationRecoversFromAShortRetryAfterAndCountsTheResend() {
        SendingCheck check = new SendingCheck();
        HttpRequestResponse base = new ScriptedProbeHttp(RetryAfterInvocationTest::ok)
                .sendRequest(ImmutableHttpRequest.post("https://mcp.test/mcp", Map.of(), "{}"));
        ScriptedProbeHttp http = new ScriptedProbeHttp(this::rateLimitedOnce);

        new RequestCheckAdapter(AdapterFixtures.settingsAllowing(true), eventLog, check).doCheck(base, null, http);

        assertThat(check.statuses).containsExactly(200);
        assertThat(http.sent()).isEqualTo(2);
        assertThat(eventLog.snapshot()).extracting(McpEventLog.LogEntry::message)
                .anyMatch(line -> line.matches("Scan check: sender — finished in \\d+ms, 2 requests, 0 issues"));
    }

    @Test
    void anInvocationOfACancelledEngineScanDoesNotWaitOutRetryAfter() {
        SendingCheck check = new SendingCheck();
        HttpRequestResponse base = new ScriptedProbeHttp(RetryAfterInvocationTest::ok)
                .sendRequest(ImmutableHttpRequest.post("https://mcp.test/mcp", Map.of(), "{}"));
        ScriptedProbeHttp http = new ScriptedProbeHttp(request ->
                FakeMcpServer.exchange(request, 429, Map.of("Retry-After", List.of("60")), ""));
        RequestCheckAdapter adapter = new RequestCheckAdapter(AdapterFixtures.settingsAllowing(true), eventLog, check);

        CurrentScan.within(Optional.of(new ScanContext(1, () -> true)), () -> adapter.doCheck(base, null, http));

        assertThat(check.statuses).containsExactly(429);
        assertThat(http.sent()).isEqualTo(1);
    }

    private ProtocolHttpResponse rateLimitedOnce(ProtocolHttpRequest request) {
        if (received.getAndIncrement() == 0) {
            return FakeMcpServer.exchange(request, 429, Map.of("Retry-After", List.of("0")), "");
        }
        return ok(request);
    }

    private static ProtocolHttpResponse ok(ProtocolHttpRequest request) {
        return FakeMcpServer.exchange(request, 200, Map.of(), "{}");
    }

    private static final class SendingCheck implements RequestCheck {

        final List<Integer> statuses = new ArrayList<>();

        @Override
        public CheckDescriptor descriptor() {
            return RecordingChecks.descriptorFor("sender", CheckKind.REQUEST);
        }

        @Override
        public List<AuditIssue> audit(HttpRequestResponse base, Http http) {
            statuses.add((int) http.sendRequest(base.request()).response().statusCode());
            return List.of();
        }
    }
}
