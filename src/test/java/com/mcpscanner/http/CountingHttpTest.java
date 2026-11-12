package com.mcpscanner.http;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.HttpMode;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CountingHttpTest {

    private final Http delegate = mock(Http.class);
    private final HttpRequestResponse reply = mock(HttpRequestResponse.class);

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        when(delegate.sendRequest(any(HttpRequest.class))).thenReturn(reply);
        when(delegate.sendRequest(any(HttpRequest.class), any(HttpMode.class))).thenReturn(reply);
        when(delegate.sendRequests(any())).thenReturn(List.of(reply, reply));
    }

    @Test
    void countsEverySendThroughEveryOverload() {
        RequestTally tally = RequestTally.started();
        Http http = CountingHttp.counting(delegate, tally);

        http.sendRequest(request());
        http.sendRequest(request(), HttpMode.AUTO);
        http.sendRequests(List.of(request(), request()));

        assertThat(tally.requests()).isEqualTo(4);
    }

    @Test
    void returnsTheDelegatesReplyUnchanged() {
        Http http = CountingHttp.counting(delegate, RequestTally.started());

        assertThat(http.sendRequest(request())).isSameAs(reply);
    }

    private static HttpRequest request() {
        return HttpRequest.httpRequestFromUrl("http://127.0.0.1/mcp");
    }
}
