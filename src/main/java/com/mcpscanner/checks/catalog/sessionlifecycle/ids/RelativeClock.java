package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

final class RelativeClock implements IdClock {

    private static final Duration SLACK = Duration.ofSeconds(2);

    @Override
    public ClockReading read(List<BigInteger> timestamps, IdSample sample, int timestampWidth) {
        BigInteger span = timestamps.get(timestamps.size() - 1).subtract(timestamps.get(0));
        long openingMillis = sample.openingTime().toMillis();
        if (!nonDecreasing(timestamps) || span.compareTo(BigInteger.valueOf(openingMillis + SLACK.toMillis())) > 0) {
            return ClockReading.notFollowed();
        }
        return new ClockReading(true, "Its " + timestampWidth + "-bit millisecond timestamp advances with the clock: "
                + "the IDs' timestamps span " + span + " ms, and the scanner took " + openingMillis
                + " ms to open the sessions.");
    }

    private static boolean nonDecreasing(List<BigInteger> timestamps) {
        return IntStream.range(1, timestamps.size())
                .allMatch(index -> timestamps.get(index).compareTo(timestamps.get(index - 1)) >= 0);
    }
}
