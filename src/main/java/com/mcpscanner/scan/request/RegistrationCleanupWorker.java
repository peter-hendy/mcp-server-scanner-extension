package com.mcpscanner.scan.request;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.logging.McpEventLog;

import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class RegistrationCleanupWorker implements RegistrationCleanupQueue {

    public static final int CAPACITY = 256;
    private static final String QUEUE_FULL = "cleanup queue full";
    private static final String DISCARDED_AT_UNLOAD = "cleanup discarded at unload";

    private final int capacity;
    private final AbandonedRegistrations abandoned;
    private final McpEventLog eventLog;
    private final Set<HttpRequestResponse> pending = Collections.newSetFromMap(new IdentityHashMap<>());
    private final ScheduledThreadPoolExecutor executor;
    private boolean stopped;

    public RegistrationCleanupWorker(int capacity, ThreadFactory threads, AbandonedRegistrations abandoned,
                                     McpEventLog eventLog) {
        this.capacity = capacity;
        this.abandoned = abandoned;
        this.eventLog = eventLog;
        this.executor = new ScheduledThreadPoolExecutor(1, threads, new ThreadPoolExecutor.DiscardPolicy());
        this.executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    @Override
    public void enqueue(HttpRequestResponse registration, RegisteredClientCleanup cleanup) {
        Optional<String> refusal = admit(registration);
        if (refusal.isPresent()) {
            abandoned.abandon(registration, refusal.get());
            return;
        }
        executor.execute(() -> clean(registration, cleanup));
    }

    public void runAfter(Duration delay, Runnable task) {
        executor.schedule(() -> runLogging(task), delay.toNanos(), TimeUnit.NANOSECONDS);
    }

    public void stop() {
        List<HttpRequestResponse> discarded = drain();
        executor.shutdownNow();
        discarded.forEach(registration -> abandoned.abandon(registration, DISCARDED_AT_UNLOAD));
    }

    private void clean(HttpRequestResponse registration, RegisteredClientCleanup cleanup) {
        try {
            cleanup.deleteClientRegisteredBy(registration);
        } catch (RuntimeException failure) {
            eventLog.error("Registration cleanup failed: " + failure, failure);
            reportLeftBehindUnlessDiscarded(registration, failure);
        } finally {
            release(registration);
        }
    }

    private void reportLeftBehindUnlessDiscarded(HttpRequestResponse registration, RuntimeException failure) {
        if (isPending(registration)) {
            abandoned.abandon(registration, "cleanup failed: " + failure.getClass().getSimpleName());
        }
    }

    private void runLogging(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException failure) {
            eventLog.error("Registration cleanup: delayed task failed: " + failure, failure);
        }
    }

    private synchronized Optional<String> admit(HttpRequestResponse registration) {
        if (stopped) {
            return Optional.of(DISCARDED_AT_UNLOAD);
        }
        if (pending.size() >= capacity) {
            return Optional.of(QUEUE_FULL);
        }
        pending.add(registration);
        return Optional.empty();
    }

    private synchronized boolean isPending(HttpRequestResponse registration) {
        return pending.contains(registration);
    }

    private synchronized void release(HttpRequestResponse registration) {
        pending.remove(registration);
    }

    private synchronized List<HttpRequestResponse> drain() {
        stopped = true;
        List<HttpRequestResponse> discarded = List.copyOf(pending);
        pending.clear();
        return discarded;
    }
}
