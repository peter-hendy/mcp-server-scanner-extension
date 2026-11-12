package com.mcpscanner.checks.api;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.RawHttpRequestStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReplyRequirementTest {

    private static final String NOTIFICATION =
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\",\"params\":{\"progress\":1}}";
    private static final String REQUEST = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"ping\",\"params\":{\"a\":1}}";

    private final Http http = mock(Http.class);

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void checksRunOnNotificationsByDefault() {
        RecordingChecks.Request check = new RecordingChecks.Request("r", List.of());

        assertThat(check.requiresReply()).isFalse();
        assertThat(ReplyRequirement.permits(check, base(NOTIFICATION))).isTrue();
    }

    @Test
    void replyReadingChecksSkipNotificationsButNotRequests() {
        RecordingChecks.Request check = new RecordingChecks.Request("r", List.of()).requiringReply();

        assertThat(ReplyRequirement.permits(check, base(NOTIFICATION))).isFalse();
        assertThat(ReplyRequirement.permits(check, base(REQUEST))).isTrue();
    }

    @Test
    void requestWithNullIdStillExpectsAReply() {
        RecordingChecks.Request check = new RecordingChecks.Request("r", List.of()).requiringReply();

        assertThat(ReplyRequirement.permits(check,
                base("{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"ping\"}"))).isTrue();
    }

    @Test
    void checkRunnerSkipsNotificationsForEveryKindThatRequiresAReply() {
        HttpRequestResponse notification = base(NOTIFICATION);
        HttpRequestResponse request = base(REQUEST);
        AuditInsertionPoint point = mock(AuditInsertionPoint.class);
        CheckRunner runner = new CheckRunner(http, base -> List.of(point));
        RecordingChecks.InsertionPoint fieldCheck = new RecordingChecks.InsertionPoint("i", List.of()).requiringReply();
        RecordingChecks.Request requestCheck = new RecordingChecks.Request("r", List.of()).requiringReply();
        RecordingChecks.Host hostCheck = new RecordingChecks.Host("h", List.of()).requiringReply();

        runner.run(fieldCheck, List.of(notification, request));
        runner.run(requestCheck, List.of(notification, request));
        runner.run(hostCheck, List.of(notification, request));

        assertThat(fieldCheck.calls).extracting(RecordingChecks.Call::base).containsExactly(request);
        assertThat(requestCheck.calls).extracting(RecordingChecks.Call::base).containsExactly(request);
        assertThat(hostCheck.calls).extracting(RecordingChecks.Call::base).containsExactly(request);
    }

    @Test
    void checkRunnerStillAuditsNotificationsForChecksThatDoNotNeedAReply() {
        HttpRequestResponse notification = base(NOTIFICATION);
        RecordingChecks.Request check = new RecordingChecks.Request("r", List.of());

        new CheckRunner(http, base -> List.of()).run(check, List.of(notification));

        assertThat(check.calls).extracting(RecordingChecks.Call::base).containsExactly(notification);
    }

    static HttpRequestResponse base(String body) {
        HttpRequestResponse base = mock(HttpRequestResponse.class);
        var request = RawHttpRequestStub.post(body, Map.of());
        when(base.request()).thenReturn(request);
        var service = request.httpService();
        when(base.httpService()).thenReturn(service);
        return base;
    }
}
