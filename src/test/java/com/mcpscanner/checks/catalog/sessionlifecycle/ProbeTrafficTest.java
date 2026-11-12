package com.mcpscanner.checks.catalog.sessionlifecycle;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProbeTrafficTest {

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void theLatestExchangeSinceAMarkIsTheFinalRetryAndNothingSentMeansNone() {
        ProbeTraffic traffic = new ProbeTraffic();
        traffic.accept(exchange("before"));
        int mark = traffic.mark();

        assertThat(traffic.latestSince(mark)).isEmpty();

        traffic.accept(exchange("challenged"));
        traffic.accept(exchange("retried"));
        assertThat(traffic.latestSince(mark)).hasValueSatisfying(latest ->
                assertThat(latest.request().path()).isEqualTo("/retried"));
    }

    private static HttpRequestResponse exchange(String path) {
        return HttpRequestResponse.httpRequestResponse(HttpRequest.httpRequestFromUrl("http://t.test/" + path), null);
    }
}
