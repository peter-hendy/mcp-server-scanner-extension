package com.mcpscanner.ui.widgets;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class OffEdtFeed<T> {

    private static final long NEVER = Long.MIN_VALUE / 2;

    private final UiReads reads;
    private final long minIntervalMillis;
    private final Supplier<T> source;
    private final Consumer<T> apply;
    private final AtomicBoolean pending = new AtomicBoolean();
    private final AtomicBoolean waitingLater = new AtomicBoolean();
    private volatile boolean cancelled;
    private volatile long lastReadAt = NEVER;

    OffEdtFeed(UiReads reads, long minIntervalMillis, Supplier<T> source, Consumer<T> apply) {
        this.reads = reads;
        this.minIntervalMillis = minIntervalMillis;
        this.source = source;
        this.apply = apply;
    }

    public void request() {
        if (!cancelled && pending.compareAndSet(false, true)) {
            reads.schedule(this::deliver, delayMillis());
        }
    }

    public void requestLater(long delayMillis) {
        if (!cancelled && waitingLater.compareAndSet(false, true)) {
            reads.schedule(() -> {
                waitingLater.set(false);
                request();
            }, delayMillis);
        }
    }

    public void cancel() {
        cancelled = true;
    }

    private long delayMillis() {
        return Math.max(0, lastReadAt + minIntervalMillis - reads.nowMillis());
    }

    private void deliver() {
        pending.set(false);
        if (cancelled) {
            return;
        }
        lastReadAt = reads.nowMillis();
        T value = source.get();
        reads.onEdt(() -> applyUnlessCancelled(value));
    }

    private void applyUnlessCancelled(T value) {
        if (!cancelled) {
            apply.accept(value);
        }
    }
}
