package com.mcpscanner.http;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public final class RequestTally implements ExchangeCounter {

    private static final double MILLIS_PER_SECOND = 1_000.0;

    private final Clock clock;
    private final Instant started;
    private final AtomicInteger requests = new AtomicInteger();

    private RequestTally(Clock clock) {
        this.clock = clock;
        this.started = clock.instant();
    }

    public static RequestTally started() {
        return startedOn(Clock.systemUTC());
    }

    public static RequestTally startedOn(Clock clock) {
        return new RequestTally(clock);
    }

    public void record() {
        requests.incrementAndGet();
    }

    @Override
    public HttpRequestResponse counted(HttpRequestResponse exchange) {
        record();
        return exchange;
    }

    public int requests() {
        return requests.get();
    }

    public String summary(String operation) {
        int count = requests();
        return operation + ": " + count + (count == 1 ? " request, " : " requests, ") + seconds() + " s";
    }

    private String seconds() {
        long millis = Duration.between(started, clock.instant()).toMillis();
        return String.format(Locale.ROOT, "%.2f", millis / MILLIS_PER_SECOND);
    }
}
