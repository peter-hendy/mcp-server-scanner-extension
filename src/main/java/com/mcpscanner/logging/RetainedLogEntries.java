package com.mcpscanner.logging;

import com.mcpscanner.logging.McpEventLog.LogEntry;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class RetainedLogEntries {

    enum Retention { DISPOSABLE, ROUTINE, KEPT }

    private final int capacity;
    private final int keptCapacity;
    private final Deque<LogEntry> entries;
    private final Map<Retention, Integer> counts = new EnumMap<>(Retention.class);

    RetainedLogEntries(int capacity) {
        this.capacity = capacity;
        this.keptCapacity = capacity / 2;
        this.entries = new ArrayDeque<>(capacity);
        for (Retention retention : Retention.values()) {
            counts.put(retention, 0);
        }
    }

    void add(LogEntry entry) {
        if (entries.size() == capacity) {
            evict(victim());
        }
        entries.addLast(entry);
        counts.merge(retentionOf(entry), 1, Integer::sum);
    }

    List<LogEntry> snapshot() {
        return List.copyOf(entries);
    }

    private Retention victim() {
        if (counts.get(Retention.DISPOSABLE) > 0) {
            return Retention.DISPOSABLE;
        }
        if (counts.get(Retention.KEPT) > keptCapacity || counts.get(Retention.ROUTINE) == 0) {
            return Retention.KEPT;
        }
        return Retention.ROUTINE;
    }

    private void evict(Retention victim) {
        entries.stream()
                .filter(entry -> retentionOf(entry) == victim)
                .findFirst()
                .ifPresent(entry -> {
                    entries.removeFirstOccurrence(entry);
                    counts.merge(victim, -1, Integer::sum);
                });
    }

    private static Retention retentionOf(LogEntry entry) {
        if (entry.level() == McpEventLog.Level.DEBUG) {
            return Retention.DISPOSABLE;
        }
        return entry.kept() ? Retention.KEPT : Retention.ROUTINE;
    }
}
