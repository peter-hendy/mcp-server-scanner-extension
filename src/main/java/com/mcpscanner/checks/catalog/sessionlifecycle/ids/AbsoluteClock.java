package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class AbsoluteClock implements IdClock {

    private static final Duration SKEW = Duration.ofDays(1);
    private static final BigInteger NANOS_PER_SECOND = BigInteger.valueOf(1_000_000_000L);
    private static final int LARGEST_SECONDS_BITS = 40;

    private final Instant epoch;
    private final BigInteger nanosPerTick;

    AbsoluteClock(Instant epoch, long nanosPerTick) {
        this.epoch = epoch;
        this.nanosPerTick = BigInteger.valueOf(nanosPerTick);
    }

    @Override
    public ClockReading read(List<BigInteger> timestamps, IdSample sample, int timestampWidth) {
        List<Optional<Instant>> instants = timestamps.stream().map(this::instant).toList();
        if (!instants.stream().allMatch(instant -> instant.filter(at -> near(at, sample)).isPresent())) {
            return ClockReading.notFollowed();
        }
        List<Instant> issued = instants.stream().flatMap(Optional::stream).sorted().toList();
        return new ClockReading(true, "Its " + timestampWidth + "-bit timestamp is the time the session was issued: "
                + "the IDs decode to times from " + millis(issued.get(0)) + " to " + millis(issued.get(issued.size() - 1))
                + ", and the scanner opened the sessions from " + millis(sample.firstRequested()) + " to "
                + millis(sample.lastAnswered()) + ".");
    }

    private Optional<Instant> instant(BigInteger ticks) {
        BigInteger[] seconds = ticks.multiply(nanosPerTick).divideAndRemainder(NANOS_PER_SECOND);
        if (seconds[0].bitLength() > LARGEST_SECONDS_BITS) {
            return Optional.empty();
        }
        return Optional.of(epoch.plusSeconds(seconds[0].longValue()).plusNanos(seconds[1].longValue()));
    }

    private static boolean near(Instant at, IdSample sample) {
        return !at.isBefore(sample.firstRequested().minus(SKEW)) && !at.isAfter(sample.lastAnswered().plus(SKEW));
    }

    private static Instant millis(Instant at) {
        return at.truncatedTo(ChronoUnit.MILLIS);
    }
}
