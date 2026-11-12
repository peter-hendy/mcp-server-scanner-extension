package com.mcpscanner.checks.shared.session;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.mcp.ScannerSentinels;

import java.util.List;

final class ScannerSentinelRemoval {

    private ScannerSentinelRemoval() {
    }

    static HttpRequest from(HttpRequest probe) {
        List<HttpHeader> sentinels = probe.headers().stream()
                .filter(header -> ScannerSentinels.ALL.contains(header.name())).toList();
        return sentinels.isEmpty() ? probe : probe.withRemovedHeaders(sentinels);
    }
}
