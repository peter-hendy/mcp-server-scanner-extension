package com.mcpscanner.proxy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PendingResponseBuffer {
    private final LinkedHashMap<String, String> responses = new LinkedHashMap<>();
    private long totalBytes;

    List<String> put(String id, String response, long budgetBytes) {
        String replaced = responses.put(id, response);
        if (replaced != null) {
            totalBytes -= sizeOf(replaced);
        }
        totalBytes += sizeOf(response);
        return evictOldestBeyond(budgetBytes);
    }

    String remove(String id) {
        String removed = responses.remove(id);
        if (removed != null) {
            totalBytes -= sizeOf(removed);
        }
        return removed;
    }

    int size() {
        return responses.size();
    }

    long totalBytes() {
        return totalBytes;
    }

    void clear() {
        responses.clear();
        totalBytes = 0;
    }

    private List<String> evictOldestBeyond(long budgetBytes) {
        List<String> evicted = new ArrayList<>();
        Iterator<Map.Entry<String, String>> oldestFirst = responses.entrySet().iterator();
        while (totalBytes > budgetBytes && oldestFirst.hasNext()) {
            Map.Entry<String, String> oldest = oldestFirst.next();
            totalBytes -= sizeOf(oldest.getValue());
            oldestFirst.remove();
            evicted.add(oldest.getKey());
        }
        return evicted;
    }

    private static long sizeOf(String response) {
        return response.getBytes(StandardCharsets.UTF_8).length;
    }
}
