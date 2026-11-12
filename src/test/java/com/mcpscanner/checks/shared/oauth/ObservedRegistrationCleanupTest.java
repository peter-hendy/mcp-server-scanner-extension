package com.mcpscanner.checks.shared.oauth;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static com.mcpscanner.checks.shared.oauth.ScriptedHttp.json;
import static com.mcpscanner.checks.shared.oauth.ScriptedHttp.status;
import static com.mcpscanner.checks.shared.oauth.ScriptedHttp.withHeader;
import static org.assertj.core.api.Assertions.assertThat;

class ObservedRegistrationCleanupTest {

    private static final String ENDPOINT = "https://auth.example.com/register";
    private static final String MANAGED = "https://auth.example.com/register/c1";
    private static final String OTHER_ENDPOINT = "https://other.example.com/register";
    private static final String OTHER_MANAGED = "https://other.example.com/register/c9";
    private static final String LEFT_ON_AUTH = "Registered OAuth client left on https://auth.example.com ";
    private static final String DELETE_MANAGED = "; delete it manually: registration_client_uri " + MANAGED;

    private final MutableClock clock = new MutableClock();
    private final List<DelayedTask> timer = new ArrayList<>();
    private final McpEventLog eventLog = new McpEventLog(null);
    private ScriptedHttp http;
    private ObservedRegistrationCleanup cleanup;

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        http = new ScriptedHttp().on("DELETE", MANAGED, status(204)).on("DELETE", OTHER_MANAGED, status(204));
        cleanup = new ObservedRegistrationCleanup(http.http(), clock,
                (delay, task) -> timer.add(new DelayedTask(delay, task)), new AbandonedClientLog(eventLog));
    }

    private static String managed(String uri, String token) {
        return "{\"client_id\":\"c1\",\"registration_client_uri\":\"" + uri + "\","
                + "\"registration_access_token\":\"" + token + "\"}";
    }

    private static HttpRequestResponse registration(String endpoint, String rawResponse) {
        return HttpRequestResponse.httpRequestResponse(
                HttpRequest.httpRequestFromUrl(endpoint).withMethod("POST").withBody("{\"redirect_uris\":[]}"),
                HttpResponse.httpResponse(rawResponse));
    }

    @Test
    void deletesTheClientAnObservedRegistrationCreatedWithItsAccessToken() {
        boolean deleted = cleanup.deleteClientRegisteredBy(registration(ENDPOINT, json(201, managed(MANAGED, "rat-1"))));

        assertThat(deleted).isTrue();
        assertThat(http.sentTo(MANAGED)).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("DELETE");
            assertThat(request.headerValue("Authorization")).isEqualTo("Bearer rat-1");
        });
    }

    @Test
    void neverDeletesAClientManagedOffTheRegistrationOrigin() {
        boolean deleted = cleanup.deleteClientRegisteredBy(
                registration(ENDPOINT, json(201, managed(OTHER_MANAGED, "rat-1"))));

        assertThat(deleted).isFalse();
        assertThat(http.sent()).isEmpty();
    }

    @Test
    void needsARegistrationAccessTokenToDelete() {
        boolean deleted = cleanup.deleteClientRegisteredBy(registration(ENDPOINT, json(201,
                "{\"client_id\":\"c1\",\"registration_client_uri\":\"" + MANAGED + "\"}")));

        assertThat(deleted).isFalse();
        assertThat(http.sent()).isEmpty();
    }

    @Test
    void ignoresARegistrationTheServerRefused() {
        boolean deleted = cleanup.deleteClientRegisteredBy(registration(ENDPOINT, json(400, managed(MANAGED, "rat-1"))));

        assertThat(deleted).isFalse();
        assertThat(http.sent()).isEmpty();
    }

    @Test
    void reportsADeletionTheServerRefusedAsNotDeleted() {
        http.on("DELETE", MANAGED, status(401));

        assertThat(cleanup.deleteClientRegisteredBy(registration(ENDPOINT, json(201, managed(MANAGED, "rat-1")))))
                .isFalse();
    }

    @Test
    void pausesCleaningUpAServerOnceItsRegistrationEndpointAnswersTooManyRequests() {
        cleanup.deleteClientRegisteredBy(registration(ENDPOINT, status(429)));

        boolean deleted = cleanup.deleteClientRegisteredBy(registration(ENDPOINT, json(201, managed(MANAGED, "rat-1"))));

        assertThat(deleted).isFalse();
        assertThat(http.sent()).isEmpty();
    }

    @Test
    void pausesCleaningUpAServerOnceADeletionIsAnsweredTooManyRequests() {
        http.on("DELETE", MANAGED, status(429));
        cleanup.deleteClientRegisteredBy(registration(ENDPOINT, json(201, managed(MANAGED, "rat-1"))));

        cleanup.deleteClientRegisteredBy(registration(ENDPOINT, json(201, managed(MANAGED, "rat-2"))));

        assertThat(http.sentTo(MANAGED)).hasSize(1);
    }

    @Test
    void aRateLimitedServerDoesNotStopCleanupOnAnotherServer() {
        cleanup.deleteClientRegisteredBy(registration(ENDPOINT, status(429)));

        boolean deleted = cleanup.deleteClientRegisteredBy(
                registration(OTHER_ENDPOINT, json(201, managed(OTHER_MANAGED, "rat-9"))));

        assertThat(deleted).isTrue();
        assertThat(http.sentTo(OTHER_MANAGED)).hasSize(1);
    }

    @Test
    void resumesCleaningUpAfterTheDefaultCoolDown() {
        cleanup.deleteClientRegisteredBy(registration(ENDPOINT, status(429)));

        assertThat(timer).singleElement().extracting(DelayedTask::delay).isEqualTo(Duration.ofSeconds(60));
        elapseCoolDown();

        assertThat(cleanup.deleteClientRegisteredBy(registration(ENDPOINT, json(201, managed(MANAGED, "rat-1")))))
                .isTrue();
        assertThat(http.sentTo(MANAGED)).hasSize(1);
    }

    @Test
    void deletesClientsRetainedDuringThePauseOnceItEnds() {
        http.on("DELETE", MANAGED, status(429));
        cleanup.deleteClientRegisteredBy(registration(ENDPOINT, json(201, managed(MANAGED, "rat-1"))));
        cleanup.deleteClientRegisteredBy(registration(ENDPOINT, json(201, managed(MANAGED, "rat-2"))));
        assertThat(http.sentTo(MANAGED)).hasSize(1);

        http.on("DELETE", MANAGED, status(204));
        elapseCoolDown();

        assertThat(http.sentTo(MANAGED)).extracting(request -> request.headerValue("Authorization"))
                .containsExactly("Bearer rat-1", "Bearer rat-1", "Bearer rat-2");
    }

    @Test
    void honoursTheRetryAfterSecondsOfTheRateLimitedReply() {
        cleanup.deleteClientRegisteredBy(registration(ENDPOINT, withHeader(429, "Retry-After", "120")));
        clock.advance(Duration.ofSeconds(90));
        cleanup.deleteClientRegisteredBy(registration(ENDPOINT, json(201, managed(MANAGED, "rat-1"))));

        assertThat(timer).first().extracting(DelayedTask::delay).isEqualTo(Duration.ofSeconds(120));
        assertThat(http.sentTo(MANAGED)).isEmpty();

        elapseCoolDown();

        assertThat(http.sentTo(MANAGED)).hasSize(1);
    }

    @Test
    void keepsTheServerPausedWhenAnotherRateLimitExtendsThePause() {
        cleanup.deleteClientRegisteredBy(registration(ENDPOINT, status(429)));
        clock.advance(Duration.ofSeconds(30));
        cleanup.deleteClientRegisteredBy(registration(ENDPOINT, status(429)));
        cleanup.deleteClientRegisteredBy(registration(ENDPOINT, json(201, managed(MANAGED, "rat-1"))));

        clock.advance(Duration.ofSeconds(30));
        runDueTimers();
        assertThat(http.sentTo(MANAGED)).isEmpty();

        elapseCoolDown();
        assertThat(http.sentTo(MANAGED)).hasSize(1);
    }

    @Test
    void retainsAtMostABoundedNumberOfClientsDuringAPause() {
        cleanup.deleteClientRegisteredBy(registration(ENDPOINT, status(429)));
        IntStream.range(0, OriginCleanup.MAX_RETAINED + 5).forEach(client ->
                cleanup.deleteClientRegisteredBy(registration(ENDPOINT, json(201, managed(MANAGED, "rat-" + client)))));

        elapseCoolDown();

        assertThat(http.sentTo(MANAGED)).hasSize(OriginCleanup.MAX_RETAINED);
    }

    @Test
    void logsEveryClientBeyondTheRetainedBoundAsLeftBehind() {
        cleanup.deleteClientRegisteredBy(registration(ENDPOINT, status(429)));
        IntStream.range(0, OriginCleanup.MAX_RETAINED + 2).forEach(client ->
                cleanup.deleteClientRegisteredBy(registration(ENDPOINT, json(201, managed(MANAGED, "rat-" + client)))));

        assertThat(loggedLines()).containsExactly(
                LEFT_ON_AUTH + "(retention full during a rate-limit pause)" + DELETE_MANAGED,
                LEFT_ON_AUTH + "(retention full during a rate-limit pause)" + DELETE_MANAGED);
    }

    @Test
    void logsEveryRetainedClientAsLeftBehindWhenCleanupIsAbandoned() {
        cleanup.deleteClientRegisteredBy(registration(ENDPOINT, status(429)));
        cleanup.deleteClientRegisteredBy(registration(ENDPOINT, json(201, managed(MANAGED, "rat-1"))));
        cleanup.deleteClientRegisteredBy(registration(ENDPOINT, json(201, managed(MANAGED, "rat-2"))));

        cleanup.abandonRetainedClients();
        elapseCoolDown();

        assertThat(http.sent()).isEmpty();
        assertThat(loggedLines()).containsExactly(
                LEFT_ON_AUTH + "(retained during a rate-limit pause at unload)" + DELETE_MANAGED,
                LEFT_ON_AUTH + "(retained during a rate-limit pause at unload)" + DELETE_MANAGED);
    }

    private List<String> loggedLines() {
        return eventLog.snapshot().stream().map(McpEventLog.LogEntry::message).toList();
    }

    private void elapseCoolDown() {
        while (!timer.isEmpty()) {
            DelayedTask next = timer.remove(0);
            clock.advance(next.delay());
            next.task().run();
        }
    }

    private void runDueTimers() {
        List<DelayedTask> due = List.copyOf(timer);
        timer.clear();
        due.forEach(task -> task.task().run());
    }

    private record DelayedTask(Duration delay, Runnable task) {
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-07T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
