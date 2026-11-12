package com.mcpscanner.checks.catalog.crossclientresponseleak;

import com.mcpscanner.checks.catalog.crossclientresponseleak.TrialOutcome.Verdict;

import java.util.Optional;

record Opening<T>(Optional<T> opened, Optional<Verdict> refusedWith) {

    static <T> Opening<T> of(T value) {
        return new Opening<>(Optional.of(value), Optional.empty());
    }

    static <T> Opening<T> refused(Verdict refusal) {
        return new Opening<>(Optional.empty(), Optional.of(refusal));
    }

    Opening<T> laterOpening() {
        return refusedWith.filter(Verdict.MODERN_ONLY_SERVER::equals).isPresent()
                ? refused(Verdict.NO_PROBE_SESSION) : this;
    }

    Verdict refusal() {
        return refusedWith.orElseThrow();
    }
}
