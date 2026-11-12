package com.mcpscanner.proxy;

import com.mcpscanner.mcp.ScannerSentinels;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

final class SentinelHeaders {

    private SentinelHeaders() {}

    static boolean hasStripAuth(Map<String, String> headers) {
        return carries(headers, ScannerSentinels.STRIP_AUTH_HEADER);
    }

    static boolean hasIdentity(Map<String, String> headers) {
        return carries(headers, ScannerSentinels.IDENTITY_HEADER);
    }

    static boolean hasKeepSession(Map<String, String> headers) {
        return carries(headers, ScannerSentinels.KEEP_SESSION_HEADER);
    }

    private static boolean carries(Map<String, String> headers, String sentinel) {
        return headers.keySet().stream().anyMatch(sentinel::equalsIgnoreCase);
    }

    static Map<String, String> withoutSentinels(Map<String, String> headers) {
        Map<String, String> stripped = new LinkedHashMap<>(headers);
        stripped.keySet().removeIf(ScannerSentinels.STRIPPED_BEFORE_UPSTREAM::contains);
        return Collections.unmodifiableMap(stripped);
    }

    static Map<String, String> withoutAuditCapability(Map<String, String> headers) {
        Map<String, String> stripped = new HashMap<>(headers);
        stripped.keySet().removeIf(ScannerSentinels.AUDIT_CAPABILITY_HEADER::equalsIgnoreCase);
        return stripped;
    }
}
