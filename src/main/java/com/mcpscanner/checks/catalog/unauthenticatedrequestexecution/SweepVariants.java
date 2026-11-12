package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.shared.auth.AuthVariant;
import com.mcpscanner.checks.shared.auth.AuthVariantSuite;
import com.mcpscanner.checks.shared.auth.VariantFamily;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class SweepVariants {

    private final AuthVariantSuite suite;
    private final RetainedSessionVariant retainedSession;
    private final Set<VariantFamily> families;

    SweepVariants(AuthVariantSuite suite, RetainedSessionVariant retainedSession, Set<VariantFamily> families) {
        this.suite = suite;
        this.retainedSession = retainedSession;
        this.families = families;
    }

    List<AuthVariant> variantsFor(HttpRequest seed) {
        List<AuthVariant> variants = new ArrayList<>();
        retainedSession.variantFor(seed).ifPresent(variants::add);
        suite.variantsFor(seed, families).stream()
                .filter(variant -> !AnonymousExecutionTier.VARIANT_NAME.equals(variant.name()))
                .forEach(variants::add);
        return List.copyOf(variants);
    }
}
