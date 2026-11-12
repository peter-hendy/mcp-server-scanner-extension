package com.mcpscanner.checks.catalog.sessionlifecycle;

import com.mcpscanner.checks.api.SkipReason;
import com.mcpscanner.checks.catalog.sessionlifecycle.ids.IdSample;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

record OpenedProbes<S>(List<String> issuedIds, List<S> owned, Instant firstRequested, Instant lastAnswered) {

    IdSample idSample() {
        return new IdSample(issuedIds, firstRequested, lastAnswered);
    }

    Optional<SkipReason> skipReason(ProbeTraffic traffic) {
        if (traffic.throttled()) {
            return Optional.of(SkipReason.rateLimited());
        }
        if (issuedIds.isEmpty()) {
            return Optional.of(SkipReason.notApplicable("the server issued no session ID to a new probe session"));
        }
        if (issuedIds.size() < 2) {
            return Optional.of(SkipReason.notApplicable("the server refused a second session"));
        }
        return Optional.empty();
    }
}
