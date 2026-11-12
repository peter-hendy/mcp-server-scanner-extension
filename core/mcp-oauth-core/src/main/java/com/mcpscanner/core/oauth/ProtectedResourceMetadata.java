package com.mcpscanner.core.oauth;

import java.net.URI;
import java.util.List;
import java.util.Set;

public record ProtectedResourceMetadata(URI resource, List<URI> issuers, Set<String> scopes) {
    public ProtectedResourceMetadata { issuers = List.copyOf(issuers); scopes = Set.copyOf(scopes); }
    @Override public String toString() { return "ProtectedResourceMetadata[redacted]"; }
}
