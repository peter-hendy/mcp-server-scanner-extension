package com.mcpscanner.mcp;

import burp.api.montoya.http.message.responses.HttpResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Pattern;

public final class RetryAfter {

    public static final String HEADER = "Retry-After";

    private static final Pattern DELAY_SECONDS = Pattern.compile("\\d+");
    private static final int MAXIMUM_EXACT_DIGITS = 18;

    private RetryAfter() {
    }

    public static Optional<Duration> of(HttpResponse response, Clock clock) {
        return value(response).flatMap(value -> parsedSeconds(value).or(() -> untilDate(value, clock)));
    }

    public static Optional<Duration> delaySeconds(HttpResponse response) {
        return value(response).flatMap(RetryAfter::parsedSeconds);
    }

    private static Optional<String> value(HttpResponse response) {
        return Optional.ofNullable(response.headerValue(HEADER)).map(String::strip);
    }

    private static Optional<Duration> parsedSeconds(String value) {
        if (!DELAY_SECONDS.matcher(value).matches()) {
            return Optional.empty();
        }
        long seconds = value.length() > MAXIMUM_EXACT_DIGITS ? Long.MAX_VALUE : Long.parseLong(value);
        return Optional.of(Duration.ofSeconds(seconds));
    }

    private static Optional<Duration> untilDate(String value, Clock clock) {
        return HttpDates.formats(clock).stream()
                .flatMap(format -> parsed(value, format).stream())
                .findFirst()
                .map(date -> remaining(date, clock));
    }

    private static Optional<Instant> parsed(String value, DateTimeFormatter format) {
        try {
            return Optional.of(ZonedDateTime.parse(value, format).toInstant());
        } catch (DateTimeParseException unparseable) {
            return Optional.empty();
        }
    }

    private static Duration remaining(Instant date, Clock clock) {
        Duration remaining = Duration.between(clock.instant(), date);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }
}
