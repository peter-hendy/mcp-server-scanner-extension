package com.mcpscanner.integration.slowlink;

import java.util.Comparator;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

record DelayedChunk(long dueNanos, long sequence, byte[] bytes) implements Delayed {

    private static final Comparator<DelayedChunk> DELIVERY_ORDER =
            Comparator.comparingLong(DelayedChunk::dueNanos).thenComparingLong(DelayedChunk::sequence);

    boolean endOfStream() {
        return bytes.length == 0;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(dueNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed other) {
        return DELIVERY_ORDER.compare(this, (DelayedChunk) other);
    }
}
