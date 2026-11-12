package com.mcpscanner.checks.api;

import com.mcpscanner.checks.shared.oauth.OAuthHttp;

import java.util.Optional;

public final class OAuthSkipLog {

    private final CheckLog log;
    private final OAuthHttp http;
    private boolean interruptionLogged;

    public OAuthSkipLog(CheckLog log, OAuthHttp http) {
        this.log = log;
        this.http = http;
    }

    public void notRun(String subject, SkipReason reason) {
        Optional<SkipReason> interruption = interruption(reason.detail());
        interruptionLogged |= interruption.isPresent();
        log.notRun(subject, interruption.orElse(reason));
    }

    public void stoppedIfRateLimited(String subject) {
        if (http.rateLimited() && !interruptionLogged) {
            log.notRun(subject, SkipReason.rateLimited());
        }
    }

    private Optional<SkipReason> interruption(String detail) {
        return http.noAnswer().map(noAnswer -> switch (noAnswer) {
            case RATE_LIMITED -> SkipReason.rateLimited();
            case STREAMED_REPLY -> SkipReason.streamedReply(detail);
            case NO_RESPONSE -> SkipReason.transportFailure(detail);
        });
    }
}
