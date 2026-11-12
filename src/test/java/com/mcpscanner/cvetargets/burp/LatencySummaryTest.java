package com.mcpscanner.cvetargets.burp;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LatencySummaryTest {

    @Test
    void medianIsTheMiddleLatencyWhateverTheOrder() {
        LatencySummary summary = LatencySummary.ofMicros(List.of(9_000L, 1_000L, 5_000L));

        assertThat(summary.median()).isEqualTo(Duration.ofMillis(5));
    }

    @Test
    void p95IsTheNearestRankNinetyFifthPercentile() {
        LatencySummary summary = LatencySummary.ofMicros(LongStream.rangeClosed(1, 100).map(ms -> ms * 1_000).boxed().toList());

        assertThat(summary.p95()).isEqualTo(Duration.ofMillis(95));
        assertThat(summary.median()).isEqualTo(Duration.ofMillis(50));
    }

    @Test
    void refusesNoLatencies() {
        assertThatThrownBy(() -> LatencySummary.ofMicros(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
