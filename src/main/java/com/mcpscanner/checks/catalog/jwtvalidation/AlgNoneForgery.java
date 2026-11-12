package com.mcpscanner.checks.catalog.jwtvalidation;

import java.util.List;

final class AlgNoneForgery implements ForgeryStrategy {

    @Override
    public List<ForgeryAttempt> attemptsFor(ForgeryContext context) {
        return List.of(ForgeryAttempt.uncontrolled(new UnsignedSignature(context.captured())));
    }
}
