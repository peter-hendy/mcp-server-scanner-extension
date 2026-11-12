package com.mcpscanner.automation;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.checks.shared.session.StreamingFallback;
import com.mcpscanner.checks.shared.stream.JdkStreamOpener;
import com.mcpscanner.checks.shared.stream.StreamLimits;
import com.mcpscanner.http.OutsideBurpSends;
import com.mcpscanner.http.wire.OneConnectionServer;
import com.mcpscanner.testutil.ClosedPort;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class ProbeCounterTest {

    private final ProbeCounter counter = ProbeCounter.throughTheExtensionProxy();
    private final Http delegate = mock(Http.class);
    private final Http counting = counter.wrap(delegate);

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void aForeignHostProbeTheStreamingFallbackCouldNotConnectForIsNotCounted() throws IOException {
        sendForeignHostProbeOutsideBurp(ClosedPort.onLoopback());

        assertThat(counter.sent()).isZero();
    }

    @Test
    void aForeignHostProbeTheStreamingFallbackSentIsCounted() throws Exception {
        try (OneConnectionServer server = OneConnectionServer.replyingWith(
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 2\r\n\r\n{}")) {
            sendForeignHostProbeOutsideBurp(server.port());

            assertThat(server.received().head()).contains("Host: attacker.example");
            assertThat(counter.sent()).isEqualTo(1);
        }
    }

    @Test
    void aStreamTheOpenerNeverSentIsNotCounted() {
        new JdkStreamOpener(StreamLimits.standard()).reportingTo(counting)
                .open(HttpRequest.httpRequestFromUrl("http://target.test/mcp").withMethod("CONNECT"));

        assertThat(counter.sent()).isZero();
    }

    @Test
    void countsEverySendAndOnlyRepliesFromTheTargetAsReached() {
        HttpRequestResponse answered = exchange(200, "{\"result\":{}}");
        HttpRequestResponse rateLimited = exchange(429, "");
        HttpRequestResponse fabricated = exchange(502, "{\"error\":\"upstream unreachable\"}");
        when(delegate.sendRequest(any(HttpRequest.class))).thenReturn(answered, rateLimited, fabricated, null);

        for (int send = 0; send < 4; send++) {
            counting.sendRequest(mock(HttpRequest.class));
        }

        assertThat(counter.sent()).isEqualTo(4);
        assertThat(counter.reached()).isEqualTo(1);
    }

    @Test
    void countsAnExchangeSentOutsideBurpByTheSameSentAndReachedRules() {
        HttpRequestResponse unanswered = mock(HttpRequestResponse.class);

        OutsideBurpSends.report(counting, exchange(200, "{\"result\":{}}"));
        OutsideBurpSends.report(counting, unanswered);

        assertThat(counter.sent()).isEqualTo(2);
        assertThat(counter.reached()).isEqualTo(1);
    }

    @Test
    void passesAnExchangeSentOutsideBurpOnToTheHttpItWraps() {
        Http observing = mock(Http.class, withSettings().extraInterfaces(OutsideBurpSends.class));
        HttpRequestResponse answered = exchange(200, "{}");

        OutsideBurpSends.report(counter.wrap(observing), answered);

        verify((OutsideBurpSends) observing).sentOutsideBurp(answered);
    }

    @Test
    void neverCountsABuiltInSinkReplyAsReached() {
        HttpRequestResponse sunk = exchange(404, "");
        when(sunk.response().hasHeader(SinkReply.HEADER)).thenReturn(true);
        when(delegate.sendRequest(any(HttpRequest.class))).thenReturn(sunk);

        counting.sendRequest(mock(HttpRequest.class));

        assertThat(counter.sent()).isEqualTo(1);
        assertThat(counter.reached()).isZero();
    }

    @Test
    void theMarkingWrapperTagsEveryCheckRequestSoBurpsHandlerCanTellItFromBuiltInTraffic() {
        HttpRequest probe = mock(HttpRequest.class);
        HttpRequest marked = mock(HttpRequest.class);
        when(probe.withAddedHeader(CheckProbeMarker.HEADER, "1")).thenReturn(marked);
        HttpRequestResponse answered = exchange(200, "{}");
        when(delegate.sendRequest(marked)).thenReturn(answered);

        assertThat(counter.wrapMarking(delegate).sendRequest(probe)).isSameAs(answered);
        assertThat(counter.sent()).isEqualTo(1);
    }

    @Test
    void thePlainWrapperSendsTheCheckRequestUnchanged() {
        HttpRequest probe = mock(HttpRequest.class);
        HttpRequestResponse answered = exchange(200, "{}");
        when(delegate.sendRequest(probe)).thenReturn(answered);

        counting.sendRequest(probe);

        verify(delegate).sendRequest(probe);
        verify(probe, org.mockito.Mockito.never()).withAddedHeader(any(String.class), any(String.class));
    }

    @Test
    void forwardsEveryVariantToTheDelegateUnchanged() {
        HttpRequest request = mock(HttpRequest.class);
        RequestOptions options = mock(RequestOptions.class);
        HttpRequestResponse answered = exchange(200, "{}");
        when(delegate.sendRequest(request, options)).thenReturn(answered);
        when(delegate.sendRequests(List.of(request))).thenReturn(List.of(answered));

        assertThat(counting.sendRequest(request, options)).isSameAs(answered);
        assertThat(counting.sendRequests(List.of(request))).containsExactly(answered);
        counting.cookieJar();

        verify(delegate).cookieJar();
        assertThat(counter.sent()).isEqualTo(2);
    }

    @Test
    void resetForgetsEverythingCounted() {
        HttpRequestResponse answered = exchange(200, "{}");
        when(delegate.sendRequest(any(HttpRequest.class))).thenReturn(answered);
        counting.sendRequest(mock(HttpRequest.class));

        counter.reset();

        assertThat(counter.sent()).isZero();
        assertThat(counter.reached()).isZero();
    }

    private void sendForeignHostProbeOutsideBurp(int port) {
        when(delegate.sendRequest(any(HttpRequest.class)))
                .thenThrow(new IllegalStateException("Streaming response received"));

        StreamingFallback.outsideBurp().around(counting).sendRequest(HttpRequest
                .httpRequestFromUrl("http://127.0.0.1:" + port + "/mcp").withHeader("Host", "attacker.example"));
    }

    private static HttpRequestResponse exchange(int status, String body) {
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn((short) status);
        when(response.bodyToString()).thenReturn(body);
        HttpRequestResponse exchange = mock(HttpRequestResponse.class);
        when(exchange.response()).thenReturn(response);
        return exchange;
    }
}
