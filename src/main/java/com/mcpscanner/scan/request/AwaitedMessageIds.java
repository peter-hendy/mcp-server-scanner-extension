package com.mcpscanner.scan.request;

import java.util.LinkedHashMap;
import java.util.Map;

final class AwaitedMessageIds {

    static final int CAPACITY = 4_096;

    private final Map<Integer, Boolean> awaited = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Boolean> eldest) {
            return size() > CAPACITY;
        }
    };

    synchronized void add(int messageId) {
        awaited.put(messageId, Boolean.TRUE);
    }

    synchronized boolean remove(int messageId) {
        return awaited.remove(messageId) != null;
    }
}
