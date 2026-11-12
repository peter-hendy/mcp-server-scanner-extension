package com.mcpscanner.cvetargets.requests;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransportRouteTest {

    @Test
    void anAnsweredProbeReachedTheTarget() {
        assertThat(TransportRoute.DIRECT.reachedTarget(answered(404))).isTrue();
    }

    @Test
    void anUnansweredProbeNeverReachedTheTarget() {
        assertThat(TransportRoute.DIRECT.reachedTarget(mock(HttpRequestResponse.class))).isFalse();
    }

    @Test
    void aRateLimitedProbeWasRefusedBeforeItExercisedTheTarget() {
        assertThat(TransportRoute.DIRECT.reachedTarget(answered(429))).isFalse();
    }


    private static HttpRequestResponse answered(int status) {
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn((short) status);
        HttpRequestResponse exchange = mock(HttpRequestResponse.class);
        when(exchange.response()).thenReturn(response);
        return exchange;
    }
}
