package com.mcpscanner.ui.scans;

import java.util.ArrayDeque;
import java.util.Deque;

final class ManualDelay implements Delay {

    record Pending(long millis, Runnable task) {
    }

    final Deque<Pending> pending = new ArrayDeque<>();

    @Override
    public void after(long millis, Runnable task) {
        pending.addLast(new Pending(millis, task));
    }

    long nextMillis() {
        return pending.getFirst().millis();
    }

    void runNext() {
        pending.removeFirst().task().run();
    }
}
