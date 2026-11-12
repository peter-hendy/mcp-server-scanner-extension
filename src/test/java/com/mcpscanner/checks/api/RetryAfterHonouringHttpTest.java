package com.mcpscanner.checks.api;

import burp.api.montoya.http.HttpMode;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.shared.scancontext.ScanNotices;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.ImmutableHttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.SendOnlyBurpHttp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RetryAfterHonouringHttpTest {

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-10-21T07:27:00Z"), ZoneOffset.UTC);

    private final ScriptedHttp server = new ScriptedHttp();
    private final List<Duration> waits = new ArrayList<>();
    private final McpEventLog eventLog = new McpEventLog(null);
    private final ScanNotices notices = new ScanNotices();
    private RetryAfterHonouringHttp http;

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        http = honouring(RetryAfterWaits.onAnyThread(() -> true));
    }

    private RetryAfterHonouringHttp honouring(RetryAfterWaits rule) {
        return new RetryAfterHonouringHttp(server, new CheckLog(eventLog, "demo"), notices::firstTime, rule,
                (wait, stillWanted) -> {
                    waits.add(wait);
                    return stillWanted.getAsBoolean();
                }, NOW);
    }

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    void waitsOutAShortRetryAfterAndResendsTheRequestOnce() {
        server.replies(429, "30").replies(200, null);

        HttpRequestResponse exchange = http.sendRequest(request("mcp.test"));

        assertThat(exchange.response().statusCode()).isEqualTo((short) 200);
        assertThat(waits).containsExactly(Duration.ofSeconds(30));
        assertThat(server.sent).hasSize(2);
    }

    @Test
    void resendsThroughTheSameSendVariant() {
        server.replies(429, "1").replies(200, null);

        http.sendRequest(request("mcp.test"), HttpMode.HTTP_1);

        assertThat(server.variants).containsExactly("mode HTTP_1", "mode HTTP_1");
    }

    @Test
    void waitsOutARetryAfterOfExactlyFiveMinutes() {
        server.replies(429, "300").replies(200, null);

        assertThat(http.sendRequest(request("mcp.test")).response().statusCode()).isEqualTo((short) 200);
        assertThat(waits).containsExactly(RetryAfterHonouringHttp.MAX_WAIT);
    }

    @Test
    void waitsUntilAnHttpDate() {
        server.replies(429, "Wed, 21 Oct 2026 07:28:00 GMT").replies(200, null);

        http.sendRequest(request("mcp.test"));

        assertThat(waits).containsExactly(Duration.ofSeconds(60));
    }

    @Test
    void returnsTheHttp429WithoutWaitingWhenRetryAfterIsOverFiveMinutes() {
        server.replies(429, "301");

        assertThat(http.sendRequest(request("mcp.test")).response().statusCode()).isEqualTo((short) 429);
        assertThat(waits).isEmpty();
        assertThat(server.sent).hasSize(1);
    }

    @Test
    void returnsTheHttp429WithoutWaitingWhenThereIsNoRetryAfter() {
        server.replies(429, null);

        assertThat(http.sendRequest(request("mcp.test")).response().statusCode()).isEqualTo((short) 429);
        assertThat(waits).isEmpty();
        assertThat(server.sent).hasSize(1);
    }

    @Test
    void returnsASecondHttp429AsItIs() {
        server.replies(429, "1").replies(429, "1");

        HttpRequestResponse exchange = http.sendRequest(request("mcp.test"));

        assertThat(exchange.response().statusCode()).isEqualTo((short) 429);
        assertThat(waits).hasSize(1);
        assertThat(server.sent).hasSize(2);
    }

    @Test
    void anInterruptedWaitReturnsTheFirstHttp429WithoutResendingAndKeepsTheInterrupt() {
        server.replies(429, "30").replies(200, null);
        http = new RetryAfterHonouringHttp(server, new CheckLog(eventLog, "demo"), notices::firstTime,
                RetryAfterWaits.onAnyThread(() -> true), (wait, stillWanted) -> {
                    throw new InterruptedException();
                }, NOW);

        HttpRequestResponse exchange = http.sendRequest(request("mcp.test"));

        assertThat(exchange.response().statusCode()).isEqualTo((short) 429);
        assertThat(server.sent).hasSize(1);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void batchSendsHonourRetryAfterPerRequest() {
        server.replies(429, "2").replies(200, null).replies(200, null);

        List<HttpRequestResponse> exchanges = http.sendRequests(List.of(request("mcp.test"), request("mcp.test")));

        assertThat(exchanges).extracting(exchange -> exchange.response().statusCode())
                .containsExactly((short) 200, (short) 200);
        assertThat(server.sent).hasSize(3);
    }

    @Test
    void logsTheFirstHttp429FromAHostAtInfoAndLaterOnesAtDebug() {
        server.replies(429, "30").replies(200, null).replies(429, "600").replies(429, null).replies(429, null);

        http.sendRequest(request("mcp.test"));
        http.sendRequest(request("mcp.test"));
        http.sendRequest(request("mcp.test"));
        http.sendRequest(request("other.test"));

        assertThat(eventLog.snapshot()).extracting(entry -> entry.level() + " " + entry.message()).containsExactly(
                "INFO Scan check: demo — HTTP 429 from mcp.test: waiting 30 s as Retry-After asks, then resending",
                "INFO Scan check: demo — HTTP 429 from mcp.test: Retry-After 600 s is over 5 min; not waiting",
                "DEBUG Scan check: demo — HTTP 429 from mcp.test: no Retry-After; not waiting",
                "INFO Scan check: demo — HTTP 429 from other.test: no Retry-After; not waiting");
    }

    @Test
    void theFirstRealWaitOnAHostIsLoggedAtInfoAfterAnEarlierNotWaitingLine() {
        server.replies(429, null).replies(429, "30").replies(200, null);

        http.sendRequest(request("mcp.test"));
        http.sendRequest(request("mcp.test"));

        assertThat(eventLog.snapshot()).extracting(entry -> entry.level() + " " + entry.message()).containsExactly(
                "INFO Scan check: demo — HTTP 429 from mcp.test: no Retry-After; not waiting",
                "INFO Scan check: demo — HTTP 429 from mcp.test: waiting 30 s as Retry-After asks, then resending");
    }

    @Test
    void aWaitNoLongerWantedReturnsTheHttp429WithoutResendingAndLogsAtDebug() {
        server.replies(429, "30").replies(200, null);
        http = honouring(RetryAfterWaits.onAnyThread(() -> false));

        HttpRequestResponse exchange = http.sendRequest(request("mcp.test"));

        assertThat(exchange.response().statusCode()).isEqualTo((short) 429);
        assertThat(server.sent).hasSize(1);
        assertThat(eventLog.snapshot()).extracting(entry -> entry.level() + " " + entry.message()).contains(
                "DEBUG Scan check: demo — HTTP 429 from mcp.test: wait abandoned (no longer wanted); "
                        + "returning the 429 without resending");
    }

    @Test
    void aHttp429OnAnotherThreadIsReturnedUnchangedWithoutWaiting() throws Exception {
        server.replies(429, "30").replies(200, null);
        http = honouring(RetryAfterWaits.onThisThreadOnly(() -> true));
        AtomicReference<HttpRequestResponse> concurrent = new AtomicReference<>();

        Thread probe = new Thread(() -> concurrent.set(http.sendRequest(request("mcp.test"))));
        probe.start();
        probe.join();

        assertThat(concurrent.get().response().statusCode()).isEqualTo((short) 429);
        assertThat(waits).isEmpty();
        assertThat(server.sent).hasSize(1);
        assertThat(eventLog.snapshot()).extracting(entry -> entry.level() + " " + entry.message()).containsExactly(
                "INFO Scan check: demo — HTTP 429 from mcp.test: not waiting on a concurrent probe");
    }

    @Test
    void aHttp429OnTheThreadThatBuiltTheRuleIsWaitedOut() {
        server.replies(429, "30").replies(200, null);
        http = honouring(RetryAfterWaits.onThisThreadOnly(() -> true));

        assertThat(http.sendRequest(request("mcp.test")).response().statusCode()).isEqualTo((short) 200);
        assertThat(waits).containsExactly(Duration.ofSeconds(30));
    }

    private static HttpRequest request(String host) {
        return ImmutableHttpRequest.post("https://" + host + "/mcp", Map.of(), "{}");
    }

    private static final class ScriptedHttp extends SendOnlyBurpHttp {

        private final Deque<Reply> scripted = new ArrayDeque<>();
        final List<HttpRequest> sent = new ArrayList<>();
        final List<String> variants = new ArrayList<>();

        ScriptedHttp replies(int status, String retryAfter) {
            scripted.add(new Reply(status, retryAfter));
            return this;
        }

        @Override
        public HttpRequestResponse sendRequest(HttpRequest request) {
            variants.add("plain");
            return reply(request);
        }

        @Override
        public HttpRequestResponse sendRequest(HttpRequest request, HttpMode httpMode) {
            variants.add("mode " + httpMode);
            return reply(request);
        }

        @Override
        public HttpRequestResponse sendRequest(HttpRequest request, RequestOptions requestOptions) {
            variants.add("options");
            return reply(request);
        }

        private HttpRequestResponse reply(HttpRequest request) {
            sent.add(request);
            Reply next = scripted.remove();
            return HttpRequestResponse.httpRequestResponse(request, rawResponse(next.status(), next.headers(), new byte[0]));
        }
    }

    private record Reply(int status, String retryAfter) {

        Map<String, List<String>> headers() {
            return retryAfter == null ? Map.of() : Map.of("Retry-After", List.of(retryAfter));
        }
    }
}
