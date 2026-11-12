package com.mcpscanner.core.connection;

import java.util.Map;
import java.util.Optional;

final class Headers {
    private Headers() {}

    static Optional<String> first(Map<String, String> headers, String name) {
        return headers.entrySet().stream()
            .filter(header -> header.getKey().equalsIgnoreCase(name))
            .map(Map.Entry::getValue)
            .findFirst();
    }
}
