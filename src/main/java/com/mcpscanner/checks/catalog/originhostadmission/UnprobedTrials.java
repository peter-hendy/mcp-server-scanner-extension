package com.mcpscanner.checks.catalog.originhostadmission;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.api.SkipReason;

import com.mcpscanner.mcp.TooManyRequests;
import java.util.Optional;

final class UnprobedTrials {

    private UnprobedTrials() {
    }

    static Optional<SkipReason> requestTrial(TrialOutcome outcome) {
        return stopReason(outcome, SkipReason.transportFailure("the replayed request got no response"),
                SkipReason.prerequisiteMissing(
                        "the replayed request returned no successful result to compare the probes with"));
    }

    static Optional<SkipReason> streamTrial(TrialOutcome outcome) {
        return stopReason(outcome, SkipReason.transportFailure("the same-origin control stream got no response"),
                SkipReason.notApplicable("the same-origin control stream did not open"));
    }

    private static Optional<SkipReason> stopReason(TrialOutcome outcome, SkipReason unanswered, SkipReason refused) {
        if (outcome.status() != TrialOutcome.TrialStatus.BASELINE_NOT_SUCCESSFUL) {
            return Optional.empty();
        }
        HttpRequestResponse baseline = outcome.baseline();
        if (baseline == null || baseline.response() == null) {
            return Optional.of(unanswered);
        }
        return Optional.of(TooManyRequests.answered(baseline) ? SkipReason.rateLimited() : refused);
    }
}
