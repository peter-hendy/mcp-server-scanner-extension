package com.mcpscanner.checks.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public record OwnedObjects(Map<String, String> entered, Map<String, String> mined) {

    public static final OwnedObjects NONE = new OwnedObjects(Map.of(), Map.of());

    public OwnedObjects {
        entered = copy(entered);
        mined = copy(mined);
    }

    public static OwnedObjects entered(Map<String, String> entered) {
        return new OwnedObjects(entered, Map.of());
    }

    public Map<String, String> forTool(boolean takesMinedValues) {
        if (!takesMinedValues) {
            return entered;
        }
        Map<String, String> arguments = new LinkedHashMap<>(entered);
        mined.forEach(arguments::putIfAbsent);
        return Collections.unmodifiableMap(arguments);
    }

    public Set<String> minedArguments(boolean takesMinedValues) {
        if (!takesMinedValues) {
            return Set.of();
        }
        Set<String> filled = new LinkedHashSet<>(mined.keySet());
        filled.removeAll(entered.keySet());
        return Collections.unmodifiableSet(filled);
    }

    public Map<String, String> all() {
        return forTool(true);
    }

    public boolean isEmpty() {
        return entered.isEmpty() && mined.isEmpty();
    }

    private static Map<String, String> copy(Map<String, String> values) {
        return values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
