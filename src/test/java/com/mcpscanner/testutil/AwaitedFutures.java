package com.mcpscanner.testutil;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public final class AwaitedFutures {

    private AwaitedFutures() {
    }

    public static <F extends Future<?>> F doneWithin(F future, Duration bound) {
        Polling.within(bound).until(future::isDone);
        return future;
    }

    public static <T> T valueWithin(Duration bound, CompletableFuture<T> future) {
        return doneWithin(future, bound).join();
    }
}
