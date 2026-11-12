package com.mcpscanner.checks.catalog.oauthredirectpolicy;

final class Outcomes {

    private Outcomes() {
    }

    static AuthorizationOutcome redirect(String location) {
        return new AuthorizationOutcome(302, location, true);
    }

    static AuthorizationOutcome status(int status) {
        return new AuthorizationOutcome(status, null, true);
    }
}
