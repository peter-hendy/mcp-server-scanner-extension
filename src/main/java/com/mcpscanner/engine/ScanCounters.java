package com.mcpscanner.engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class ScanCounters {

    private final AtomicInteger planned = new AtomicInteger();
    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicInteger skipped = new AtomicInteger();
    private final AtomicInteger requests = new AtomicInteger();
    private final Map<RegisteredCheck, AtomicInteger> skippedByCheck = new ConcurrentHashMap<>();

    void plan(int invocations) {
        planned.addAndGet(invocations);
    }

    void complete() {
        completed.incrementAndGet();
    }

    void skip(RegisteredCheck check) {
        skipped.incrementAndGet();
        skippedByCheck.computeIfAbsent(check, ignored -> new AtomicInteger()).incrementAndGet();
    }

    void reportSkipped(SkipCause cause) {
        skippedByCheck.forEach((check, count) -> check.skips().skipped(count.get(), cause));
    }

    void requestSent() {
        requests.incrementAndGet();
    }

    int requests() {
        return requests.get();
    }

    ScanProgress progress(int issues, boolean cancelled, int watchedPayloads) {
        return new ScanProgress(planned.get(), completed.get(), skipped.get(), issues, cancelled, watchedPayloads);
    }
}
