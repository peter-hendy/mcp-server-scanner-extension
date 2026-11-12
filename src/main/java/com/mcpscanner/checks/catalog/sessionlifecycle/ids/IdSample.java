package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record IdSample(List<String> ids, Instant firstRequested, Instant lastAnswered) {

    public static final int DECISIVE_SIZE = 30;

    public IdSample {
        ids = List.copyOf(ids);
    }

    int size() {
        return ids.size();
    }

    Duration openingTime() {
        return Duration.between(firstRequested, lastAnswered);
    }
}
