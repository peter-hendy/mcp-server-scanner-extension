package com.mcpscanner.checks.catalog.crossclientresponseleak;

import com.mcpscanner.checks.api.HostSettlement;
import com.mcpscanner.checks.api.SkipReason;

import java.util.Optional;

record TrialOutcome(Verdict verdict, String argument, Optional<Crossing> crossing) {

    enum Verdict {
        NO_PROBE_SESSION(HostSettlement.INCONCLUSIVE,
                SkipReason.prerequisiteMissing("no probe session could be opened")),
        MODERN_ONLY_SERVER(HostSettlement.SETTLED, SkipReason.notApplicable("the server serves only MCP 2026-07-28"
                + " and refuses the initialize handshake, so no separate probe sessions can be opened")),
        NO_ECHOED_ARGUMENT(HostSettlement.UNSUITABLE_SEED,
                SkipReason.prerequisiteMissing("the server echoes none of the call's string arguments")),
        STATE_SHARED_BETWEEN_SEQUENTIAL_CLIENTS(HostSettlement.UNSUITABLE_SEED),
        STATE_SHARED_UNDER_CONCURRENCY(HostSettlement.SETTLED),
        INTERMITTENT_CROSSING(HostSettlement.SETTLED),
        UNANSWERED_RACE(HostSettlement.INCONCLUSIVE, SkipReason.transportFailure(
                "not every probe client got a successful reply to a race round in time")),
        RATE_LIMITED(HostSettlement.SETTLED, SkipReason.rateLimited()),
        NO_CROSSING(HostSettlement.SETTLED),
        CROSSED(HostSettlement.SETTLED);

        private final HostSettlement settlement;
        private final Optional<SkipReason> stopReason;

        Verdict(HostSettlement settlement) {
            this(settlement, null);
        }

        Verdict(HostSettlement settlement, SkipReason stopReason) {
            this.settlement = settlement;
            this.stopReason = Optional.ofNullable(stopReason);
        }

        HostSettlement settlement() {
            return settlement;
        }

        Optional<SkipReason> stopReason() {
            return stopReason;
        }
    }

    static TrialOutcome of(Verdict verdict) {
        return of(verdict, "");
    }

    static TrialOutcome of(Verdict verdict, String argument) {
        return new TrialOutcome(verdict, argument, Optional.empty());
    }

    String summary() {
        return verdict + (argument.isEmpty() ? "" : " (argument " + argument + ")");
    }
}
