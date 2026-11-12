package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import com.mcpscanner.checks.api.CheckLog;
import com.mcpscanner.checks.api.OAuthSkipLog;
import com.mcpscanner.checks.api.SkipReason;
import com.mcpscanner.checks.shared.oauth.ClientCleanup;
import com.mcpscanner.checks.shared.oauth.OAuthHttp;

import java.util.Optional;

final class DecisionLog {

    private final CheckLog log;
    private final OAuthSkipLog skips;

    DecisionLog(CheckLog log, OAuthHttp http) {
        this.log = log;
        this.skips = new OAuthSkipLog(log, http);
    }

    <T> Optional<T> notRun(String subject, SkipReason reason) {
        skips.notRun(subject, reason);
        return Optional.empty();
    }

    <T> Optional<T> notReported(String reason) {
        log.info("decision: not reported — " + reason);
        return Optional.empty();
    }

    void stoppedIfRateLimited(String subject) {
        skips.stoppedIfRateLimited(subject);
    }

    void cleanedUp(ClientCleanup cleanup) {
        if (cleanup.registered() > 0) {
            log.info(cleanup.summary());
        }
    }
}
