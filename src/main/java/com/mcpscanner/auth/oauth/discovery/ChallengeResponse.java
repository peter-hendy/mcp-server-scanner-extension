package com.mcpscanner.auth.oauth.discovery;

import java.util.Optional;
import java.util.Set;

record ChallengeResponse(int status, Optional<String> challenge) {

    private static final Set<Integer> AUTH_REQUIRED_STATUSES = Set.of(401, 403);

    boolean requiresAuth() {
        return AUTH_REQUIRED_STATUSES.contains(status);
    }

    boolean succeeded() {
        return status >= 200 && status < 300;
    }
}
