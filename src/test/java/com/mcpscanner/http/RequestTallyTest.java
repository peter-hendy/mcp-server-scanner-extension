package com.mcpscanner.http;

import com.mcpscanner.testutil.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RequestTallyTest {

    @Test
    void summarisesTheRequestsRecordedAndTheTimeSinceItStarted() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        RequestTally tally = RequestTally.startedOn(clock);

        tally.record();
        tally.record();
        clock.advance(Duration.ofMillis(1_250));

        assertThat(tally.requests()).isEqualTo(2);
        assertThat(tally.summary("Connect")).isEqualTo("Connect: 2 requests, 1.25 s");
    }

    @Test
    void namesASingleRequestInTheSingular() {
        RequestTally tally = RequestTally.startedOn(new MutableClock(Instant.EPOCH));

        tally.record();

        assertThat(tally.summary("Discovery")).isEqualTo("Discovery: 1 request, 0.00 s");
    }
}
