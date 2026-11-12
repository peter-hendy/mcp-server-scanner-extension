package com.mcpscanner.core.protocol;

import java.util.Collection;
import java.util.List;

final class HandshakeVersions {
    private static final List<String> NEWEST_FIRST = List.of(
        McpRequests.HANDSHAKE_VERSION, "2025-06-18", "2025-03-26", McpRequests.SSE_VERSION);

    private HandshakeVersions() {}

    static List<String> newestFirst() {
        return NEWEST_FIRST;
    }

    static boolean supported(String version) {
        return NEWEST_FIRST.contains(version);
    }

    static String newestOf(Collection<?> candidates) {
        return NEWEST_FIRST.stream().filter(candidates::contains).findFirst().orElse(null);
    }

    static String olderThan(String version) {
        int index = NEWEST_FIRST.indexOf(version);
        return index < 0 || index + 1 == NEWEST_FIRST.size() ? null : NEWEST_FIRST.get(index + 1);
    }

    static String describe() {
        return String.join(", ", NEWEST_FIRST);
    }
}
