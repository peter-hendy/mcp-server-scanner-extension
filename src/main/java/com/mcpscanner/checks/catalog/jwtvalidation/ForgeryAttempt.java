package com.mcpscanner.checks.catalog.jwtvalidation;

import java.util.Optional;

record ForgeryAttempt(ForgedSignature candidate, Optional<ForgedSignature> wrongKeyControl) {

    static ForgeryAttempt uncontrolled(ForgedSignature candidate) {
        return new ForgeryAttempt(candidate, Optional.empty());
    }

    static ForgeryAttempt controlledBy(ForgedSignature candidate, ForgedSignature wrongKeyControl) {
        return new ForgeryAttempt(candidate, Optional.of(wrongKeyControl));
    }
}
