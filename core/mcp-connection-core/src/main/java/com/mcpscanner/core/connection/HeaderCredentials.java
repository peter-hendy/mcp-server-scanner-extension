package com.mcpscanner.core.connection;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class HeaderCredentials implements CredentialProvider {
    private static final Set<String> RESERVED = Set.of("host", "content-length", "transfer-encoding", "connection",
            "content-type", "accept", "mcp-protocol-version", "mcp-session-id", "mcp-method", "mcp-name");
    private final Map<String, String> headers;

    HeaderCredentials(Map<String, String> headers) {
        if (headers == null) throw new IllegalArgumentException("Credential headers required");
        Set<String> names = new HashSet<>();
        for (var header : headers.entrySet()) {
            String name = header.getKey();
            String value = header.getValue();
            if (name == null || !name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+") || value == null
                    || value.chars().anyMatch(c -> c < 32 || c > 126)
                    || RESERVED.contains(name.toLowerCase(Locale.ROOT)) || !names.add(name.toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Invalid credential header");
        }
        this.headers = Map.copyOf(headers);
    }

    public Map<String, String> headers() { return headers; }
    public Set<String> headerNames() { return headers.keySet(); }
    @Override public String toString() { return "CredentialProvider[redacted]"; }
}
