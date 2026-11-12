package com.mcpscanner.checks.registry;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import com.mcpscanner.checks.api.RecordingChecks;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.RawHttpRequestStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReplyGatedAdapterTest {

    private static final String NOTIFICATION = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\","
            + "\"params\":{\"requestId\":\"x\"}}";
    private static final String REQUEST = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{}}";

    private final Http http = mock(Http.class);
    private final AuditInsertionPoint point = mock(AuditInsertionPoint.class);
    private final McpEventLog eventLog = new McpEventLog(null);

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void everyAdapterSkipsNotificationsForAReplyReadingCheck() {
        RecordingChecks.InsertionPoint field = new RecordingChecks.InsertionPoint("i", List.of()).requiringReply();
        RecordingChecks.Request request = new RecordingChecks.Request("r", List.of()).requiringReply();
        RecordingChecks.Host host = new RecordingChecks.Host("h", List.of()).requiringReply();

        for (McpCheckAdapter<?> adapter : List.of(
                new InsertionPointCheckAdapter(AdapterFixtures.settingsAllowing(true), eventLog, field),
                new RequestCheckAdapter(AdapterFixtures.settingsAllowing(true), eventLog, request),
                new HostCheckAdapter(AdapterFixtures.settingsAllowing(true), eventLog, host))) {
            assertThat(adapter.doCheck(base(NOTIFICATION), point, http).auditIssues()).isEmpty();
        }

        assertThat(field.calls).isEmpty();
        assertThat(request.calls).isEmpty();
        assertThat(host.calls).isEmpty();
        assertThat(eventLog.snapshot()).extracting(McpEventLog.LogEntry::message)
                .allMatch(line -> line.endsWith("(notification gets no reply)"))
                .anyMatch(line -> line.startsWith("Scan check: i — skipped notifications/cancelled at "))
                .hasSize(3);
    }

    @Test
    void hostIsNotClaimedByASkippedNotification() {
        RecordingChecks.Host host = new RecordingChecks.Host("h", List.of()).requiringReply();
        HostCheckAdapter adapter = new HostCheckAdapter(AdapterFixtures.settingsAllowing(true), eventLog, host);
        HttpRequestResponse request = base(REQUEST);

        adapter.doCheck(base(NOTIFICATION), point, http);
        adapter.doCheck(request, point, http);

        assertThat(host.calls).extracting(RecordingChecks.Call::base).containsExactly(request);
    }

    @Test
    void checksThatDoNotNeedAReplyStillSeeNotifications() {
        RecordingChecks.Request request = new RecordingChecks.Request("r", List.of());
        HttpRequestResponse notification = base(NOTIFICATION);

        new RequestCheckAdapter(AdapterFixtures.settingsAllowing(true), eventLog, request)
                .doCheck(notification, point, http);

        assertThat(request.calls).extracting(RecordingChecks.Call::base).containsExactly(notification);
    }

    private static HttpRequestResponse base(String body) {
        HttpRequestResponse base = mock(HttpRequestResponse.class);
        var request = RawHttpRequestStub.post(body, Map.of());
        when(base.request()).thenReturn(request);
        var service = request.httpService();
        when(base.httpService()).thenReturn(service);
        return base;
    }
}
