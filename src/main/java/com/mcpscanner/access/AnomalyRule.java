package com.mcpscanner.access;

import com.mcpscanner.checks.api.AccessExpectation;
import com.mcpscanner.checks.api.AccessFinding;
import com.mcpscanner.checks.api.AccessOutcome;
import com.mcpscanner.checks.api.Principal;

import java.util.List;
import java.util.Optional;

final class AnomalyRule {

    private AnomalyRule() {
    }

    static boolean identityBreaks(AccessExpectation expectation, Principal owner, Principal principal,
                                  Optional<AccessOutcome> outcome, List<AccessFinding> findings) {
        if (findings.stream().anyMatch(AccessFinding::breaksExpectation)) {
            return true;
        }
        return !owner.id().equals(principal.id()) && reachedOwnerResult(outcome)
                && expectation.forbids(principal.role());
    }

    static boolean anonymousBreaks(AccessExpectation expectation, Optional<AccessOutcome> outcome) {
        return expectation != AccessExpectation.SHARED_BY_DESIGN && reachedOwnerResult(outcome);
    }

    private static boolean reachedOwnerResult(Optional<AccessOutcome> outcome) {
        return outcome.filter(AccessOutcome.SAME_RESULT::equals).isPresent();
    }
}
