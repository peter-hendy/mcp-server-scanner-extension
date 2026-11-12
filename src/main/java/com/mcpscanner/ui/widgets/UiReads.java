package com.mcpscanner.ui.widgets;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class UiReads {

    private final ReadScheduler scheduler;
    private final Executor edt;
    private final Consumer<String> debug;

    public UiReads(ReadScheduler scheduler, Executor edt, Consumer<String> debug) {
        this.scheduler = scheduler;
        this.edt = edt;
        this.debug = debug;
    }

    public static UiReads background(ReadScheduler scheduler, Consumer<String> debug) {
        return new UiReads(scheduler, Edt::run, debug);
    }

    public void run(Runnable work) {
        schedule(work, 0);
    }

    public <T> void compute(Supplier<T> work, Consumer<T> apply) {
        run(() -> {
            T result = work.get();
            onEdt(() -> apply.accept(result));
        });
    }

    public <T> OffEdtFeed<T> feed(long minIntervalMillis, Supplier<T> source, Consumer<T> apply) {
        return new OffEdtFeed<>(this, minIntervalMillis, source, apply);
    }

    void schedule(Runnable work, long delayMillis) {
        scheduler.schedule(() -> logFailure(work), delayMillis);
    }

    long nowMillis() {
        return scheduler.nowMillis();
    }

    void onEdt(Runnable work) {
        edt.execute(work);
    }

    private void logFailure(Runnable work) {
        try {
            work.run();
        } catch (RuntimeException failure) {
            debug.accept("UI background work failed: " + failure.getClass().getSimpleName());
        }
    }
}
