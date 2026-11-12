package com.mcpscanner.proxy;

import com.mcpscanner.mcp.ScannerSentinels;

import java.util.Locale;
import java.util.Map;

final class BrowserRequestGuard {

    static final String REFUSAL_REASON = "browser request refused (Origin/Sec-Fetch header without scanner routing)";

    private static final String ORIGIN = "origin";
    private static final String SEC_FETCH_PREFIX = "sec-fetch-";

    private final LoopbackRoutingKey routingKey;

    BrowserRequestGuard(LoopbackRoutingKey routingKey) {
        this.routingKey = routingKey;
    }

    boolean refuses(Map<String, String> headers) {
        return headers.keySet().stream().anyMatch(BrowserRequestGuard::isBrowserOnly)
                && !routingKey.admits(presentedKey(headers));
    }

    private static boolean isBrowserOnly(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals(ORIGIN) || lower.startsWith(SEC_FETCH_PREFIX);
    }

    private static String presentedKey(Map<String, String> headers) {
        return headers.entrySet().stream()
                .filter(header -> header.getKey().equalsIgnoreCase(ScannerSentinels.ROUTED_HEADER))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
