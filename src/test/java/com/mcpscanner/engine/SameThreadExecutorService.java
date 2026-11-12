package com.mcpscanner.engine;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class SameThreadExecutorService extends AbstractExecutorService {

    private final int capacity;
    private final AtomicInteger accepted = new AtomicInteger();
    private volatile boolean shutdown;

    SameThreadExecutorService() {
        this(Integer.MAX_VALUE);
    }

    private SameThreadExecutorService(int capacity) {
        this.capacity = capacity;
    }

    static SameThreadExecutorService acceptingOnly(int capacity) {
        return new SameThreadExecutorService(capacity);
    }

    @Override
    public void execute(Runnable command) {
        if (shutdown || accepted.incrementAndGet() > capacity) {
            throw new RejectedExecutionException("not accepting tasks");
        }
        command.run();
    }

    @Override
    public void shutdown() {
        shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown = true;
        return List.of();
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        return shutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return shutdown;
    }
}
