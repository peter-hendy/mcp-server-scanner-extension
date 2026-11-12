package com.mcpscanner.integration.slowlink;

import java.time.Duration;

public record LatencyBudget(String operation, int requests, Duration roundTrip, int concurrency, Slack slack) {

    public Duration allowed() {
        int waves = (requests + concurrency - 1) / concurrency;
        return roundTrip.multipliedBy(waves).plus(slack.forRequests(requests));
    }

    public void assertMet(Duration elapsed) {
        if (elapsed.compareTo(allowed()) > 0) {
            throw new AssertionError(describe(elapsed));
        }
    }

    public String describe(Duration elapsed) {
        return operation + " took " + elapsed.toMillis() + " ms for " + requests + " requests; budget "
                + allowed().toMillis() + " ms (" + requests + " × " + roundTrip.toMillis() + " ms RTT ÷ "
                + concurrency + " concurrent + " + slack.describe(requests) + ")";
    }
}
