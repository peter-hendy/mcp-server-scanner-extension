package com.mcpscanner.cvetargets.run;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class ArmDeadline {

    private static final Duration DEFAULT_CHECK_INTERVAL = Duration.ofSeconds(1);

    private final Clock wallClock;
    private final Duration checkInterval;

    ArmDeadline() {
        this(Clock.systemUTC(), DEFAULT_CHECK_INTERVAL);
    }

    ArmDeadline(Clock wallClock, Duration checkInterval) {
        this.wallClock = wallClock;
        this.checkInterval = checkInterval;
    }

    <T> T within(Duration limit, Callable<T> work) {
        Instant expires = wallClock.instant().plus(limit);
        FutureTask<T> task = new FutureTask<>(work);
        Thread worker = new Thread(task, "cve-target-arm");
        worker.setDaemon(true);
        worker.start();
        try {
            return awaitUntil(expires, limit, task);
        } catch (ExecutionException failed) {
            throw unchecked(failed.getCause());
        } catch (InterruptedException interrupted) {
            task.cancel(false);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the arm", interrupted);
        }
    }

    private <T> T awaitUntil(Instant expires, Duration limit, FutureTask<T> task)
            throws ExecutionException, InterruptedException {
        while (true) {
            Duration remaining = Duration.between(wallClock.instant(), expires);
            if ((remaining.isNegative() || remaining.isZero()) && !task.isDone()) {
                task.cancel(false);
                throw new ArmTimedOut(limit);
            }
            try {
                return task.get(Math.max(1, shorter(remaining, checkInterval).toMillis()), TimeUnit.MILLISECONDS);
            } catch (TimeoutException stillRunningAtThisCheck) {
                continue;
            }
        }
    }

    private static Duration shorter(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private static RuntimeException unchecked(Throwable cause) {
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(cause);
    }
}
