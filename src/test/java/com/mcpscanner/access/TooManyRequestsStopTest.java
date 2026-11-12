package com.mcpscanner.access;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.testutil.ImmutableHttpRequest;
import com.mcpscanner.access.AccessRunStopped;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TooManyRequestsStopTest {

    private final RecordingHttp http = new RecordingHttp();

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void sendsFreelyUntilTheFirstHttp429() {
        TooManyRequestsStop stop = new TooManyRequestsStop(http);

        assertThat(stop.sendRequest(request("a")).response().statusCode()).isEqualTo((short) 200);

        assertThat(stop.reason()).isEmpty();
    }

    @Test
    void refusesEverySendAfterTheFirstHttp429() {
        http.rateLimit("a");
        TooManyRequestsStop stop = new TooManyRequestsStop(http);

        assertThat(stop.sendRequest(request("a")).response().statusCode()).isEqualTo((short) 429);

        assertThatThrownBy(() -> stop.sendRequest(request("b")))
                .isInstanceOf(AccessRunStopped.class).hasMessage(TooManyRequestsStop.REASON);
        assertThat(stop.reason()).contains(TooManyRequestsStop.REASON);
        assertThat(http.sent).extracting(HttpRequest::bodyToString).containsExactly("a");
    }

    @Test
    void stillLetsSessionDeletesThroughAfterTheStop() {
        http.rateLimit("a");
        TooManyRequestsStop stop = new TooManyRequestsStop(http);
        stop.sendRequest(request("a"));

        stop.sendRequest(request("").withMethod("DELETE"));

        assertThat(http.sent).extracting(HttpRequest::method).containsExactly("POST", "DELETE");
    }

    private static HttpRequest request(String body) {
        return ImmutableHttpRequest.post("https://mcp.test/mcp", Map.of(), body);
    }
}
