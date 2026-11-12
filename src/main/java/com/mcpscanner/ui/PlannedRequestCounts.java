package com.mcpscanner.ui;

import com.mcpscanner.ui.widgets.UiReads;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

final class PlannedRequestCounts {

    static final int CAPACITY = 16;

    private final UiReads reads;
    private final Map<Object, Integer> known = new RecentlyUsed();
    private final Set<Object> counting = new HashSet<>();
    private Runnable onCounted = () -> { };
    private long generation;

    PlannedRequestCounts(UiReads reads) {
        this.reads = reads;
    }

    void onCounted(Runnable listener) {
        onCounted = listener;
    }

    Optional<Integer> count(Object key, Supplier<Integer> plan) {
        if (!known.containsKey(key) && counting.add(key)) {
            long startedIn = generation;
            reads.compute(plan, count -> counted(key, count, startedIn));
        }
        return Optional.ofNullable(known.get(key));
    }

    void invalidate() {
        generation++;
        known.clear();
        counting.clear();
    }

    private void counted(Object key, int count, long startedIn) {
        if (startedIn != generation) {
            return;
        }
        counting.remove(key);
        known.put(key, count);
        onCounted.run();
    }

    private static final class RecentlyUsed extends LinkedHashMap<Object, Integer> {
        RecentlyUsed() {
            super(CAPACITY, 0.75f, true);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<Object, Integer> eldest) {
            return size() > CAPACITY;
        }
    }
}
