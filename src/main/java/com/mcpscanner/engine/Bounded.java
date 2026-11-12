package com.mcpscanner.engine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

final class Bounded<T> {

    record Kept<T>(List<T> latest, int total) {
    }

    private final int capacity;
    private final Deque<T> latest = new ArrayDeque<>();
    private int total;

    Bounded(int capacity) {
        this.capacity = capacity;
    }

    synchronized void add(T item) {
        total++;
        latest.addLast(item);
        if (latest.size() > capacity) {
            latest.removeFirst();
        }
    }

    synchronized Kept<T> kept() {
        return new Kept<>(List.copyOf(latest), total);
    }
}
