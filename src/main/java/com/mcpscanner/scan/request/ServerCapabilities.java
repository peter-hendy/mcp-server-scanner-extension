package com.mcpscanner.scan.request;

import java.util.HashMap;
import java.util.Map;

public record ServerCapabilities(Map<String, Object> advertised) {

    public ServerCapabilities {
        advertised = advertised == null ? Map.of() : withoutNullValues(advertised);
    }

    private static Map<String, Object> withoutNullValues(Map<String, Object> capabilities) {
        Map<String, Object> present = new HashMap<>();
        capabilities.forEach((name, value) -> {
            if (name != null && value != null) {
                present.put(name, value);
            }
        });
        return Map.copyOf(present);
    }

    public static ServerCapabilities none() {
        return new ServerCapabilities(Map.of());
    }

    public boolean advertises(String capability) {
        return advertised.containsKey(capability);
    }

    public boolean advertisesResourceSubscription() {
        return advertised.get("resources") instanceof Map<?, ?> resources
                && Boolean.TRUE.equals(resources.get("subscribe"));
    }
}
