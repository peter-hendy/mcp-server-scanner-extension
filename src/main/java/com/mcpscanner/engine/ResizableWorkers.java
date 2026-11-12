package com.mcpscanner.engine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

public final class ResizableWorkers implements EngineWorkers {

    private final ThreadPoolExecutor pool;
    private final IntSupplier workerCount;

    private ResizableWorkers(ThreadPoolExecutor pool, IntSupplier workerCount) {
        this.pool = pool;
        this.workerCount = workerCount;
    }

    public static ResizableWorkers sizedBy(IntSupplier workerCount, ThreadFactory threads) {
        int initial = workerCount.getAsInt();
        return new ResizableWorkers(new ThreadPoolExecutor(initial, initial, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), threads), workerCount);
    }

    @Override
    public ExecutorService executor() {
        return pool;
    }

    @Override
    public synchronized void applyWorkerCount() {
        int workers = workerCount.getAsInt();
        if (workers > pool.getMaximumPoolSize()) {
            growTo(workers);
        } else {
            shrinkTo(workers);
        }
    }

    private void growTo(int workers) {
        pool.setMaximumPoolSize(workers);
        pool.setCorePoolSize(workers);
    }

    private void shrinkTo(int workers) {
        pool.setCorePoolSize(workers);
        pool.setMaximumPoolSize(workers);
    }
}
