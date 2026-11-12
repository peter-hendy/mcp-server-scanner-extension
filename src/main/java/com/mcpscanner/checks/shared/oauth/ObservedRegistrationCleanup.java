package com.mcpscanner.checks.shared.oauth;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;

import java.net.URI;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ObservedRegistrationCleanup {

    private final Http http;
    private final Clock clock;
    private final DelayedTasks timer;
    private final AbandonedClientLog abandonedClients;
    private final Map<String, OriginCleanup> origins = new ConcurrentHashMap<>();

    public ObservedRegistrationCleanup(Http http, Clock clock, DelayedTasks timer,
                                       AbandonedClientLog abandonedClients) {
        this.http = http;
        this.clock = clock;
        this.timer = timer;
        this.abandonedClients = abandonedClients;
    }

    public boolean deleteClientRegisteredBy(HttpRequestResponse registration) {
        return HttpUris.parse(registration.request().url())
                .map(endpoint -> deleteClient(endpoint, registration))
                .orElse(false);
    }

    public void abandonRetainedClients() {
        origins.values().forEach(OriginCleanup::abandonRetained);
    }

    private boolean deleteClient(URI endpoint, HttpRequestResponse registration) {
        OriginCleanup origin = cleanupFor(endpoint);
        OAuthHttp observed = new OAuthHttp(http);
        boolean deleted = observed.answered(registration)
                .flatMap(JsonDocument::fromSuccessfulResponse)
                .flatMap(document -> ClientManagement.from(document, endpoint))
                .map(origin::delete)
                .orElse(false);
        observed.rateLimitReply().ifPresent(origin::pause);
        return deleted;
    }

    private OriginCleanup cleanupFor(URI endpoint) {
        return origins.computeIfAbsent(HttpUris.origin(endpoint), origin -> new OriginCleanup(http, clock, timer, abandonedClients));
    }
}
