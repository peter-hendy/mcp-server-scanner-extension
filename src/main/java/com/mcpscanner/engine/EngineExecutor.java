package com.mcpscanner.engine;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

final class EngineExecutor {

    private static final Duration TERMINATION_BOUND = Duration.ofSeconds(5);

    private final EngineWorkers workers;

    EngineExecutor(EngineWorkers workers) {
        this.workers = workers;
    }

    void applyWorkerCount() {
        workers.applyWorkerCount();
    }

    boolean isShutdown() {
        return workers.executor().isShutdown();
    }

    void execute(ScanTask<?> task) {
        try {
            workers.executor().execute(task);
        } catch (RejectedExecutionException rejected) {
            task.drop();
        }
    }

    void shutdown() {
        workers.executor().shutdownNow().stream()
                .filter(ScanTask.class::isInstance)
                .map(ScanTask.class::cast)
                .forEach(ScanTask::drop);
        awaitTermination();
    }

    private void awaitTermination() {
        try {
            workers.executor().awaitTermination(TERMINATION_BOUND.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
