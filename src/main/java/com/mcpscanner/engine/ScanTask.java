package com.mcpscanner.engine;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

final class ScanTask<T> implements Runnable {

    private final PauseGate gate;
    private final EngineExecutor executor;
    private final Supplier<T> work;
    private final Supplier<T> whenDropped;
    private final CompletableFuture<T> result = new CompletableFuture<>();

    ScanTask(PauseGate gate, EngineExecutor executor, Supplier<T> work, Supplier<T> whenDropped) {
        this.gate = gate;
        this.executor = executor;
        this.work = work;
        this.whenDropped = whenDropped;
    }

    CompletableFuture<T> result() {
        return result;
    }

    void schedule() {
        executor.execute(this);
    }

    void drop() {
        result.complete(whenDropped.get());
    }

    @Override
    public void run() {
        if (gate.parkIfPaused(this)) {
            return;
        }
        try {
            result.complete(work.get());
        } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
        } catch (Error error) {
            drop();
            throw error;
        }
    }
}
