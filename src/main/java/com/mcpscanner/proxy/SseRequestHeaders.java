package com.mcpscanner.proxy;

import com.mcpscanner.mcp.McpHeaders;

import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

final class SseRequestHeaders {

    private SseRequestHeaders() {}

    static Map<String, String> forwardable(Map<String, String> headers) {
        var connectionTokens = connectionTokens(headers);
        var forwarded = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        headers.forEach((name, value) -> {
            if (ReservedHeaderPolicy.isForwardable(name) && !connectionTokens.contains(name)) forwarded.put(name, value);
        });
        return Map.copyOf(forwarded);
    }

    static Map<String, String> forOperation(Map<String, String> sessionHeaders, Map<String, String> requestHeaders) {
        boolean stripAuth = SentinelHeaders.hasStripAuth(requestHeaders);
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        sessionHeaders.forEach((name, value) -> {
            if (!stripAuth || name.equalsIgnoreCase(McpHeaders.SESSION_ID)
                    || name.equalsIgnoreCase(McpHeaders.PROTOCOL_VERSION)) {
                headers.put(name, value);
            }
        });
        headers.putAll(requestHeaders);
        return headers;
    }

    static Map<String, String> challenge(HttpHeaders headers) {
        List<String> challenges = headers.allValues("WWW-Authenticate");
        return challenges.isEmpty() ? Map.of() : Map.of("WWW-Authenticate", String.join(", ", challenges));
    }

    private static TreeSet<String> connectionTokens(Map<String, String> headers) {
        var tokens = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        headers.forEach((name, value) -> {
            if (name.equalsIgnoreCase("Connection")) {
                for (String token : value.split(",")) {
                    tokens.add(token.trim());
                }
            }
        });
        return tokens;
    }
}
