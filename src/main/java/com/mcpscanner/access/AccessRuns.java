package com.mcpscanner.access;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public final class AccessRuns {

    private final ExecutorService worker;
    private final Set<AccessRun> active = ConcurrentHashMap.newKeySet();

    public AccessRuns(ExecutorService worker) {
        this.worker = worker;
    }

    AccessRun start(Consumer<AccessRun> work) {
        AccessRun run = new AccessRun();
        active.add(run);
        worker.execute(() -> {
            try {
                work.accept(run);
            } finally {
                active.remove(run);
            }
        });
        return run;
    }

    public void cancelAll() {
        active.forEach(AccessRun::cancel);
    }

    public void shutdown() {
        cancelAll();
        worker.shutdownNow();
    }
}
