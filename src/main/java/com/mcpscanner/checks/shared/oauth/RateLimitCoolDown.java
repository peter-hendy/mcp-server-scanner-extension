package com.mcpscanner.checks.shared.oauth;

import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.mcp.RetryAfter;

import java.time.Duration;

final class RateLimitCoolDown {

    static final Duration DEFAULT = Duration.ofSeconds(60);
    static final Duration MINIMUM = Duration.ofSeconds(1);
    static final Duration MAXIMUM = Duration.ofMinutes(15);

    private RateLimitCoolDown() {
    }

    static Duration of(HttpResponse rateLimited) {
        return RetryAfter.delaySeconds(rateLimited).map(RateLimitCoolDown::bounded).orElse(DEFAULT);
    }

    private static Duration bounded(Duration requested) {
        if (requested.compareTo(MINIMUM) < 0) {
            return MINIMUM;
        }
        return requested.compareTo(MAXIMUM) > 0 ? MAXIMUM : requested;
    }
}
