package com.mcpscanner.checks.api;

import java.time.Duration;
import java.util.function.BooleanSupplier;

final class SlicedWait implements RetryWait {

    static final Duration SLICE = Duration.ofMillis(250);

    private final Sleeper sleeper;

    SlicedWait(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    @Override
    public boolean waitedOut(Duration wait, BooleanSupplier stillWanted) throws InterruptedException {
        Duration left = wait;
        while (stillWanted.getAsBoolean()) {
            if (left.isZero() || left.isNegative()) {
                return true;
            }
            Duration slice = left.compareTo(SLICE) < 0 ? left : SLICE;
            sleeper.sleep(slice);
            left = left.minus(slice);
        }
        return false;
    }
}
