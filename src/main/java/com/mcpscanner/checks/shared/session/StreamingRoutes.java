package com.mcpscanner.checks.shared.session;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.mcp.ScannerSentinels;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class StreamingRoutes {

    private final Set<String> unproxiedUrls = ConcurrentHashMap.newKeySet();
    private final Set<String> directlyStreamingUrls = ConcurrentHashMap.newKeySet();

    void streamed(HttpRequest probe) {
        (bypassesProxy(probe) ? directlyStreamingUrls : unproxiedUrls).add(probe.url());
    }

    void forget() {
        unproxiedUrls.clear();
        directlyStreamingUrls.clear();
    }

    boolean bypassBurp(HttpRequest probe) {
        String url = probe.url();
        return unproxiedUrls.contains(url) || bypassesProxy(probe) && directlyStreamingUrls.contains(url);
    }

    private static boolean bypassesProxy(HttpRequest probe) {
        return probe.hasHeader(ScannerSentinels.DIRECT_DELIVERY_HEADER) || probe.headers().stream()
                .noneMatch(header -> ScannerSentinels.ROUTES_TO_PROXY.contains(header.name()));
    }
}
