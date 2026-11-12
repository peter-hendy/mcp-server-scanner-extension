package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class ScopeClaims {

    static final int MAX_ATTEMPTS = 3;

    private final Set<SweepScope> claimed = ConcurrentHashMap.newKeySet();
    private final Map<SweepScope, Integer> attempts = new ConcurrentHashMap<>();

    boolean claim(SweepScope scope) {
        return claimed.add(scope);
    }

    void releaseForRetry(SweepScope scope) {
        if (attempts.merge(scope, 1, Integer::sum) < MAX_ATTEMPTS) {
            claimed.remove(scope);
        }
    }

    void clear() {
        claimed.clear();
        attempts.clear();
    }
}
