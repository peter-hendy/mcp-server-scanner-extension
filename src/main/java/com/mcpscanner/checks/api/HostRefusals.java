package com.mcpscanner.checks.api;

import com.mcpscanner.checks.shared.host.Claims;

public final class HostRefusals {

    private HostRefusals() {
    }

    public static SkipReason reasonFor(Claims.Refusal refusal) {
        return switch (refusal) {
            case HELD -> SkipReason.alreadyAudited("this host was already probed");
            case PROBED_ATTEMPTS_SPENT -> SkipReason.budgetExhausted(Claims.MAX_PROBED_ATTEMPTS
                    + " inconclusive attempts on this host");
            case UNSUITABLE_SEEDS_SPENT -> SkipReason.budgetExhausted(Claims.MAX_UNSUITABLE_SEEDS
                    + " unsuitable requests on this host");
        };
    }
}
