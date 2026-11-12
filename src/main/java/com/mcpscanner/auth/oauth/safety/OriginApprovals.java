package com.mcpscanner.auth.oauth.safety;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

final class OriginApprovals {

    private final Map<String, List<Set<String>>> approvedCodeSetsByOrigin = new ConcurrentHashMap<>();

    void record(String origin, Collection<String> codes) {
        approvedCodeSetsByOrigin.computeIfAbsent(origin, ignored -> new CopyOnWriteArrayList<>())
                .add(Set.copyOf(codes));
    }

    boolean covers(String origin, Collection<String> codes) {
        return approvedCodeSetsByOrigin.getOrDefault(origin, List.of()).stream()
                .anyMatch(approved -> approved.containsAll(codes));
    }

    void clear() {
        approvedCodeSetsByOrigin.clear();
    }
}
