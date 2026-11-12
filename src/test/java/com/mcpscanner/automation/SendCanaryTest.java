package com.mcpscanner.automation;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SendCanaryTest {

    private static final String URL = "http://127.0.0.1:8123/canary";

    private final Http http = mock(Http.class);
    private final AtomicLong clock = new AtomicLong();

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void sendsTheRequestedCountToTheUrlAndTimesEachSend() {
        when(http.sendRequest(any(HttpRequest.class))).thenAnswer(invocation -> {
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(3));
            return answered();
        });

        SendCanaryReport report = new SendCanary(http, clock::get).run(new SendCanaryCommand(URL, 4, 1));

        ArgumentCaptor<HttpRequest> sent = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(4)).sendRequest(sent.capture());
        assertThat(sent.getAllValues()).allSatisfy(request -> assertThat(request.url()).isEqualTo(URL));
        assertThat(report.latenciesMicros()).containsExactly(3_000L, 3_000L, 3_000L, 3_000L);
        assertThat(report.wallMicros()).isEqualTo(12_000L);
        assertThat(report.unanswered()).isZero();
    }

    @Test
    void countsSendsWithoutAResponseOrThatThrowAsUnanswered() {
        when(http.sendRequest(any(HttpRequest.class))).thenReturn(mock(HttpRequestResponse.class))
                .thenThrow(new IllegalStateException("connection refused"))
                .thenReturn(answered());

        SendCanaryReport report = new SendCanary(http, clock::get).run(new SendCanaryCommand(URL, 3, 1));

        assertThat(report.latenciesMicros()).hasSize(3);
        assertThat(report.unanswered()).isEqualTo(2);
    }

    @Test
    void keepsTheRequestedNumberOfSendsInFlightAtOnce() {
        CountDownLatch allInFlight = new CountDownLatch(3);
        when(http.sendRequest(any(HttpRequest.class))).thenAnswer(invocation -> {
            allInFlight.countDown();
            return allInFlight.await(10, TimeUnit.SECONDS) ? answered() : null;
        });

        SendCanaryReport report = new SendCanary(http, System::nanoTime).run(new SendCanaryCommand(URL, 3, 3));

        assertThat(report.unanswered()).isZero();
    }

    @Test
    void refusesACommandOutsideItsBounds() {
        assertThatThrownBy(() -> new SendCanaryCommand("file:///etc/passwd", 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SendCanaryCommand(URL, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SendCanaryCommand(URL, SendCanaryCommand.MAX_COUNT + 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SendCanaryCommand(URL, 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SendCanaryCommand(URL, 1, SendCanaryCommand.MAX_CONCURRENCY + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static HttpRequestResponse answered() {
        HttpRequestResponse exchange = mock(HttpRequestResponse.class);
        when(exchange.response()).thenReturn(mock(HttpResponse.class));
        return exchange;
    }
}
