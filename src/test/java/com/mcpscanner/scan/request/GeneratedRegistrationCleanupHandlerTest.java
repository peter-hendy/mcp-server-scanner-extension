package com.mcpscanner.scan.request;

import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.mcp.ScannerSentinels;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeneratedRegistrationCleanupHandlerTest {

    private static final int MESSAGE_ID = 41;

    private final List<HttpRequestResponse> cleanedUp = new ArrayList<>();
    private final List<Runnable> queued = new ArrayList<>();
    private final GeneratedRegistrationCleanupHandler handler =
            new GeneratedRegistrationCleanupHandler(cleanedUp::add,
                    (registration, cleanup) -> queued.add(() -> cleanup.deleteClientRegisteredBy(registration)));
    private final HttpRequest stripped = mock(HttpRequest.class);

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    private HttpRequestToBeSent outgoing(boolean generated) {
        return outgoing(generated, MESSAGE_ID);
    }

    private HttpRequestToBeSent outgoing(boolean generated, int messageId) {
        HttpRequestToBeSent request = mock(HttpRequestToBeSent.class);
        when(request.messageId()).thenReturn(messageId);
        when(request.hasHeader(ScannerSentinels.GENERATED_REGISTRATION_HEADER)).thenReturn(generated);
        when(request.withRemovedHeader(ScannerSentinels.GENERATED_REGISTRATION_HEADER)).thenReturn(stripped);
        return request;
    }

    private HttpResponseReceived answer() {
        return answer(MESSAGE_ID);
    }

    private HttpResponseReceived answer(int messageId) {
        HttpResponseReceived response = mock(HttpResponseReceived.class);
        when(response.messageId()).thenReturn(messageId);
        when(response.initiatingRequest()).thenReturn(stripped);
        return response;
    }

    private void runQueued() {
        queued.forEach(Runnable::run);
        queued.clear();
    }

    @Test
    void stripsTheMarkerBeforeAGeneratedRegistrationLeavesBurp() {
        assertThat(handler.handleHttpRequestToBeSent(outgoing(true)).request()).isSameAs(stripped);
    }

    @Test
    void cleansUpAfterTheAnswerToAGeneratedRegistrationOffBurpsHandlerThread() {
        handler.handleHttpRequestToBeSent(outgoing(true));
        HttpResponseReceived response = answer();

        assertThat(handler.handleHttpResponseReceived(response).response()).isSameAs(response);
        assertThat(cleanedUp).isEmpty();

        runQueued();

        assertThat(cleanedUp).singleElement().satisfies(exchange -> {
            assertThat(exchange.request()).isSameAs(stripped);
            assertThat(exchange.response()).isSameAs(response);
        });
    }

    @Test
    void leavesRegistrationsItDidNotGenerateAlone() {
        HttpRequestToBeSent foreign = outgoing(false);

        assertThat(handler.handleHttpRequestToBeSent(foreign).request()).isSameAs(foreign);
        handler.handleHttpResponseReceived(answer());
        runQueued();

        assertThat(cleanedUp).isEmpty();
    }

    @Test
    void cleansUpEachGeneratedRegistrationOnce() {
        handler.handleHttpRequestToBeSent(outgoing(true));
        handler.handleHttpResponseReceived(answer());
        handler.handleHttpResponseReceived(answer());
        runQueued();

        assertThat(cleanedUp).hasSize(1);
    }

    @Test
    void forgetsTheOldestUnansweredRegistrationOnceTooManyAwaitAnAnswer() {
        int unanswered = AwaitedMessageIds.CAPACITY + 1;
        for (int messageId = 0; messageId < unanswered; messageId++) {
            handler.handleHttpRequestToBeSent(outgoing(true, messageId));
        }

        handler.handleHttpResponseReceived(answer(0));
        handler.handleHttpResponseReceived(answer(unanswered - 1));
        runQueued();

        assertThat(cleanedUp).hasSize(1);
    }
}
