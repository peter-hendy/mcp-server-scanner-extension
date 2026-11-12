package com.mcpscanner.checks.catalog.oauthredirectpolicy;

record AuthorizationOutcome(int status, String location, boolean complete) {

    static AuthorizationOutcome incomplete() {
        return new AuthorizationOutcome(0, null, false);
    }
}
