package com.mcpscanner.core.oauth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public final class OAuthTokenRefreshPolicy {
    public enum UnknownExpiry { REFRESH, WAIT_FOR_CHALLENGE }
    private static final Duration REFRESH_SKEW = Duration.ofSeconds(30);
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    private final Clock clock;
    private int consecutiveFailures;

    public OAuthTokenRefreshPolicy(Clock clock) {
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    public boolean valid(Instant expiresAt) {
        return expiresAt != null && clock.instant().isBefore(expiresAt);
    }

    public boolean needsRefresh(Instant expiresAt) {
        return needsRefresh(expiresAt, UnknownExpiry.REFRESH);
    }

    public boolean needsRefresh(Instant expiresAt, UnknownExpiry unknownExpiry) {
        if (expiresAt == null) return unknownExpiry == UnknownExpiry.REFRESH;
        return !clock.instant().isBefore(expiresAt.minus(REFRESH_SKEW));
    }

    public synchronized boolean shouldAttempt(boolean force, Instant expiresAt) {
        return !terminallyFailed() && (force || needsRefresh(expiresAt));
    }

    public synchronized void recordSuccess() {
        if (!terminallyFailed()) consecutiveFailures = 0;
    }

    public synchronized int recordFailure() {
        if (consecutiveFailures < MAX_CONSECUTIVE_FAILURES) consecutiveFailures++;
        return consecutiveFailures;
    }

    public synchronized boolean terminallyFailed() {
        return consecutiveFailures >= MAX_CONSECUTIVE_FAILURES;
    }

    public int maxConsecutiveFailures() {
        return MAX_CONSECUTIVE_FAILURES;
    }
}
