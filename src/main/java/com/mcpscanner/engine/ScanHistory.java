package com.mcpscanner.engine;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongToIntFunction;

public final class ScanHistory {

    public static final int FINISHED_KEPT = 50;

    private final Clock clock;
    private final LongToIntFunction watchedPayloads;
    private final EngineLog log;
    private final Deque<EngineScan> newestFirst = new ArrayDeque<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private long lastId;

    ScanHistory(Clock clock, LongToIntFunction watchedPayloads, EngineLog log) {
        this.clock = clock;
        this.watchedPayloads = watchedPayloads;
        this.log = log;
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    public void pause(long scanId) {
        find(scanId).ifPresent(EngineScan::pause);
    }

    public void resume(long scanId) {
        find(scanId).ifPresent(EngineScan::resume);
    }

    public void cancel(long scanId) {
        find(scanId).ifPresent(EngineScan::cancel);
    }

    public synchronized List<ScanSnapshot> snapshots() {
        return newestFirst.stream().map(EngineScan::snapshot).toList();
    }

    public void remove(long scanId) {
        if (removeEnded(scanId)) {
            changed();
        }
    }

    public void clearFinished() {
        if (removeEndedScans()) {
            changed();
        }
    }

    EngineScan start(ScanLabel label) {
        EngineScan scan = added(label);
        changed();
        return scan;
    }

    synchronized Optional<EngineScan> find(long scanId) {
        return newestFirst.stream().filter(scan -> scan.id() == scanId).findFirst();
    }

    void finished() {
        dropEarliestEndedBeyondBound();
        changed();
    }

    private synchronized EngineScan added(ScanLabel label) {
        long id = ++lastId;
        EngineScan scan = new EngineScan(id, label, clock, this::changed, () -> watchedPayloads.applyAsInt(id));
        newestFirst.addFirst(scan);
        return scan;
    }

    private synchronized boolean removeEnded(long scanId) {
        return newestFirst.removeIf(scan -> scan.id() == scanId && scan.ended());
    }

    private synchronized boolean removeEndedScans() {
        return newestFirst.removeIf(EngineScan::ended);
    }

    private synchronized void dropEarliestEndedBeyondBound() {
        newestFirst.stream()
                .filter(EngineScan::ended)
                .sorted(Comparator.comparing((EngineScan scan) -> scan.endedAt().orElse(Instant.MAX)).reversed())
                .skip(FINISHED_KEPT)
                .toList()
                .forEach(newestFirst::remove);
    }

    private void changed() {
        listeners.forEach(this::tell);
    }

    private void tell(Runnable listener) {
        try {
            listener.run();
        } catch (RuntimeException failure) {
            log.debug("history listener failed: " + FailureDescription.of(failure));
        }
    }
}
