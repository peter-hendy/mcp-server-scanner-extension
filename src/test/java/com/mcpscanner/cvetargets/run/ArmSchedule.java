package com.mcpscanner.cvetargets.run;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class ArmSchedule implements AutoCloseable {

    private final ExecutorService pool;

    public ArmSchedule(int parallelArms) {
        if (parallelArms < 1) {
            throw new IllegalArgumentException("CVE target runs need at least one parallel arm, got " + parallelArms);
        }
        AtomicInteger names = new AtomicInteger();
        pool = Executors.newFixedThreadPool(parallelArms, runnable -> {
            Thread thread = new Thread(runnable, "cve-target-schedule-" + names.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    public <T> CompletableFuture<T> submit(Supplier<T> arm) {
        return CompletableFuture.supplyAsync(arm, pool);
    }

    @Override
    public void close() {
        pool.shutdownNow();
    }
}
