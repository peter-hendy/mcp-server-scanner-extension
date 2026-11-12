package com.mcpscanner.client;

import com.mcpscanner.mcp.McpHeaders;
import com.mcpscanner.core.connection.CredentialProvider;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class ScannerHeaderState {

    private final Map<String, String> headers = new HashMap<>();
    private final Set<String> credentialHeaderNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    void replaceHeaders(Map<String, String> initialHeaders) {
        headers.clear();
        headers.putAll(initialHeaders);
    }

    void clear() {
        headers.clear();
        credentialHeaderNames.clear();
    }

    Map<String, String> snapshot() {
        return Map.copyOf(headers);
    }

    String sessionId() {
        return headers.get(McpHeaders.SESSION_ID);
    }

    Set<String> credentialHeaderNames() {
        return Set.copyOf(credentialHeaderNames);
    }

    void putAll(Map<String, String> refreshed) {
        headers.putAll(refreshed);
    }

    void adoptProtocol(String protocolVersion, String sessionId) {
        headers.remove(McpHeaders.SESSION_ID);
        adoptProtocolVersion(protocolVersion);
        if (sessionId != null) {
            headers.put(McpHeaders.SESSION_ID, sessionId);
        }
    }

    void adoptProtocolVersion(String protocolVersion) {
        headers.put(McpHeaders.PROTOCOL_VERSION, protocolVersion);
    }

    void rememberCredentials(Map<String, String> requestHeaders, Set<String> names) {
        credentialHeaderNames.addAll(names);
        headers.keySet().removeIf(existing -> names.stream().anyMatch(existing::equalsIgnoreCase));
        requestHeaders.forEach((name, value) -> {
            if (names.stream().anyMatch(name::equalsIgnoreCase)) headers.put(name, value);
        });
    }

    static Map<String, String> withCredentials(Map<String, String> baseHeaders, CredentialProvider credentials) {
        Map<String, String> merged = new HashMap<>(baseHeaders);
        if (credentials != null) {
            credentials.headerNames().forEach(name ->
                    merged.keySet().removeIf(existing -> existing.equalsIgnoreCase(name)));
            merged.putAll(credentials.headers());
        }
        return Collections.unmodifiableMap(merged);
    }
}
