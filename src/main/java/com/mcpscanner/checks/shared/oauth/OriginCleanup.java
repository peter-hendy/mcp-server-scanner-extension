package com.mcpscanner.checks.shared.oauth;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

final class OriginCleanup {

    static final int MAX_RETAINED = 1_000;
    private static final String RETENTION_FULL = "retention full during a rate-limit pause";
    private static final String RETAINED_AT_UNLOAD = "retained during a rate-limit pause at unload";

    private final Http http;
    private final Clock clock;
    private final DelayedTasks timer;
    private final AbandonedClientLog abandonedClients;
    private final Deque<ClientManagement> retained = new ArrayDeque<>();
    private Instant pausedUntil = Instant.MIN;
    private boolean resumeScheduled;

    OriginCleanup(Http http, Clock clock, DelayedTasks timer, AbandonedClientLog abandonedClients) {
        this.http = http;
        this.clock = clock;
        this.timer = timer;
        this.abandonedClients = abandonedClients;
    }

    synchronized boolean delete(ClientManagement client) {
        if (paused()) {
            retain(client);
            return false;
        }
        return attemptDeletion(client);
    }

    synchronized void pause(HttpResponse rateLimited) {
        Instant until = clock.instant().plus(RateLimitCoolDown.of(rateLimited));
        if (until.isAfter(pausedUntil)) {
            pausedUntil = until;
        }
        scheduleResume();
    }

    synchronized void abandonRetained() {
        while (!retained.isEmpty()) {
            abandonedClients.left(retained.poll(), RETAINED_AT_UNLOAD);
        }
    }

    private synchronized void resume() {
        resumeScheduled = false;
        if (paused()) {
            scheduleResume();
            return;
        }
        while (!paused() && !retained.isEmpty()) {
            attemptDeletion(retained.poll());
        }
    }

    private boolean attemptDeletion(ClientManagement client) {
        OAuthHttp attempt = new OAuthHttp(http);
        boolean deleted = new ClientDeleter(attempt).delete(client);
        attempt.rateLimitReply().ifPresent(rateLimited -> {
            pause(rateLimited);
            retain(client);
        });
        return deleted;
    }

    private void retain(ClientManagement client) {
        if (retained.size() < MAX_RETAINED) {
            retained.add(client);
        } else {
            abandonedClients.left(client, RETENTION_FULL);
        }
    }

    private boolean paused() {
        return clock.instant().isBefore(pausedUntil);
    }

    private void scheduleResume() {
        if (resumeScheduled) {
            return;
        }
        resumeScheduled = true;
        timer.runAfter(Duration.between(clock.instant(), pausedUntil), this::resume);
    }
}
