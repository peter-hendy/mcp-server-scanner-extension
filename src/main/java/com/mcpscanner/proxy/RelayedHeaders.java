package com.mcpscanner.proxy;

import com.mcpscanner.mcp.McpHeaders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class RelayedHeaders {

    private static final Set<String> HOP_OR_PROXY_OWNED = Set.of("content-length", "transfer-encoding", "connection",
            "keep-alive", "content-type", "content-encoding", McpHeaders.SESSION_ID.toLowerCase(Locale.ROOT));

    private RelayedHeaders() {}

    static Map<String, String> of(Map<String, String> upstream) {
        Map<String, String> relayed = new LinkedHashMap<>();
        upstream.forEach((name, value) -> {
            if (isRelayed(name)) relayed.put(name, value);
        });
        return relayed;
    }

    static Map<String, String> ofMultiValued(Map<String, List<String>> upstream) {
        Map<String, String> joined = new LinkedHashMap<>();
        upstream.forEach((name, values) -> joined.put(name, String.join(", ", values)));
        return of(joined);
    }

    private static boolean isRelayed(String name) {
        return name != null && !name.startsWith(":") && !HOP_OR_PROXY_OWNED.contains(name.toLowerCase(Locale.ROOT));
    }
}
