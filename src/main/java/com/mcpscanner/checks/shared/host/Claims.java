package com.mcpscanner.checks.shared.host;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class Claims<K> {

    public enum Refusal { HELD, PROBED_ATTEMPTS_SPENT, UNSUITABLE_SEEDS_SPENT }

    public static final int MAX_PROBED_ATTEMPTS = 3;
    public static final int MAX_UNSUITABLE_SEEDS = 10;

    private final Set<K> claimed = ConcurrentHashMap.newKeySet();
    private final Map<K, Integer> probedAttempts = new ConcurrentHashMap<>();
    private final Map<K, Integer> unsuitableSeeds = new ConcurrentHashMap<>();

    public boolean tryClaim(K key) {
        return claimed.add(key);
    }

    public void releaseUnprobed(K key) {
        claimed.remove(key);
    }

    public void releaseProbed(K key) {
        releaseWithin(key, probedAttempts, MAX_PROBED_ATTEMPTS);
    }

    public void releaseUnsuitableSeed(K key) {
        releaseWithin(key, unsuitableSeeds, MAX_UNSUITABLE_SEEDS);
    }

    public Refusal whyRefused(K key) {
        if (spent(key, probedAttempts, MAX_PROBED_ATTEMPTS)) {
            return Refusal.PROBED_ATTEMPTS_SPENT;
        }
        return spent(key, unsuitableSeeds, MAX_UNSUITABLE_SEEDS) ? Refusal.UNSUITABLE_SEEDS_SPENT : Refusal.HELD;
    }

    public void clear() {
        claimed.clear();
        probedAttempts.clear();
        unsuitableSeeds.clear();
    }

    private boolean spent(K key, Map<K, Integer> counts, int limit) {
        return counts.getOrDefault(key, 0) >= limit;
    }

    private void releaseWithin(K key, Map<K, Integer> spent, int limit) {
        if (spent.merge(key, 1, Integer::sum) < limit) {
            claimed.remove(key);
        }
    }
}
