package com.mcpscanner.identity;

import java.util.List;

public record IdentityOAuthSettings(String issuer, String clientId, List<String> scopes) {

    private static final IdentityOAuthSettings NONE = new IdentityOAuthSettings("", "", List.of());

    public IdentityOAuthSettings {
        issuer = issuer == null ? "" : issuer;
        clientId = clientId == null ? "" : clientId;
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }

    public static IdentityOAuthSettings none() {
        return NONE;
    }
}
