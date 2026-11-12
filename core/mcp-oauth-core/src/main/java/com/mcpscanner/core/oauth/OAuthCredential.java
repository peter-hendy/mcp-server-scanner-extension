package com.mcpscanner.core.oauth;

import java.time.Instant;
import java.util.Set;

public record OAuthCredential(CredentialBinding binding, String accessToken, String refreshToken,
        Instant expiresAt, Set<String> grantedScopes) {
    public OAuthCredential { grantedScopes = Set.copyOf(grantedScopes); }
    @Override public String toString() { return "OAuthCredential[redacted]"; }
}
