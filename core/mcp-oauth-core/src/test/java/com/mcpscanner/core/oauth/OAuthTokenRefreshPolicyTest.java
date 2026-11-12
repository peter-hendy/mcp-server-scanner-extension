package com.mcpscanner.core.oauth;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

public final class OAuthTokenRefreshPolicyTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC);

    public static void main(String[] args) {
        refreshesAtThirtySecondBoundaryAndWhenExpiryIsUnknown();
        threeConsecutiveFailuresStopRefreshUntilNewPolicyInstance();
        successResetsFailureCountAndForcedRefreshBypassesExpiry();
        System.out.println("OAuthTokenRefreshPolicyTest passed");
    }

    private static void refreshesAtThirtySecondBoundaryAndWhenExpiryIsUnknown() {
        var policy = new OAuthTokenRefreshPolicy(CLOCK);
        check(!policy.needsRefresh(CLOCK.instant().plusSeconds(31)), "Future token is usable");
        check(policy.needsRefresh(CLOCK.instant().plusSeconds(30)), "Thirty-second boundary refreshes");
        check(policy.needsRefresh(null), "Unknown expiry refreshes");
        check(!policy.needsRefresh(null, OAuthTokenRefreshPolicy.UnknownExpiry.WAIT_FOR_CHALLENGE),
                "Unknown expiry may wait for a challenge when the grant allows it");
        check(policy.valid(CLOCK.instant().plusSeconds(1)), "Future expiry is valid");
        check(!policy.valid(CLOCK.instant()), "Current expiry is invalid");
        check(!policy.valid(null), "Unknown expiry is invalid");
    }

    private static void threeConsecutiveFailuresStopRefreshUntilNewPolicyInstance() {
        var policy = new OAuthTokenRefreshPolicy(CLOCK);
        check(policy.recordFailure() == 1, "First failure counted");
        check(policy.recordFailure() == 2, "Second failure counted");
        check(!policy.terminallyFailed(), "Two failures are recoverable");
        check(policy.recordFailure() == 3, "Third failure counted");
        check(policy.terminallyFailed(), "Third failure is terminal");
        policy.recordSuccess();
        check(policy.terminallyFailed(), "Late success cannot clear terminal state");
        check(!policy.shouldAttempt(true, CLOCK.instant().plusSeconds(60)), "Terminal force refresh blocked");
        check(!policy.shouldAttempt(false, CLOCK.instant()), "Terminal proactive refresh blocked");
    }

    private static void successResetsFailureCountAndForcedRefreshBypassesExpiry() {
        var policy = new OAuthTokenRefreshPolicy(CLOCK);
        policy.recordFailure();
        policy.recordFailure();
        policy.recordSuccess();
        check(policy.recordFailure() == 1, "Success resets failure count");
        check(policy.shouldAttempt(true, CLOCK.instant().plusSeconds(60)), "Forced refresh bypasses expiry");
        check(!policy.shouldAttempt(false, CLOCK.instant().plusSeconds(60)), "Fresh token avoids refresh");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
