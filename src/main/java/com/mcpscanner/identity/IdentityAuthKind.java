package com.mcpscanner.identity;

public enum IdentityAuthKind {
    DETECT,
    NONE,
    BEARER,
    CUSTOM_HEADERS,
    OAUTH,
    OAUTH_CLIENT_CREDENTIALS,
    OAUTH_ENTERPRISE_MANAGED;

    public boolean offeredToComparedUsers() {
        return this != DETECT && !isOAuthGrant();
    }

    public boolean isOAuthGrant() {
        return this == OAUTH_CLIENT_CREDENTIALS || this == OAUTH_ENTERPRISE_MANAGED;
    }
}
