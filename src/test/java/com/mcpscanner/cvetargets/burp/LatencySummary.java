package com.mcpscanner.cvetargets.burp;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

record LatencySummary(List<Long> sortedMicros) {

    static LatencySummary ofMicros(List<Long> micros) {
        if (micros.isEmpty()) {
            throw new IllegalArgumentException("no latencies to summarise");
        }
        return new LatencySummary(micros.stream().sorted().toList());
    }

    Duration median() {
        return percentile(50);
    }

    Duration p95() {
        return percentile(95);
    }

    private Duration percentile(int percent) {
        int rank = (int) Math.ceil(percent / 100.0 * sortedMicros.size());
        return Duration.of(sortedMicros.get(Math.max(rank, 1) - 1), ChronoUnit.MICROS);
    }

    @Override
    public String toString() {
        return "median " + median().toMillis() + " ms, p95 " + p95().toMillis() + " ms over " + sortedMicros.size()
                + " sends";
    }
}
