package com.mcpscanner.core.oauth;

import java.net.URI;
import java.util.Map;

public record AuthorizationRequest(URI endpoint, Map<String, String> parameters) {
    public AuthorizationRequest { parameters = Map.copyOf(parameters); }
    public URI uri() { return URI.create(endpoint.toASCIIString() + (endpoint.getRawQuery() == null ? "?" : "&") + FormEncoding.encode(parameters)); }
    @Override public String toString() { return "AuthorizationRequest[redacted]"; }
}
