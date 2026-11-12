package com.mcpscanner.checks.catalog.sessionbinding;

import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.crossidentity.IdentityIssue;

import java.util.List;

record IdentityOutcome(Principal principal, Verdict verdict, List<IdentityIssue> issues) {

    IdentityOutcome {
        issues = List.copyOf(issues);
    }

    static IdentityOutcome unprobed(Principal principal, Verdict verdict) {
        return new IdentityOutcome(principal, verdict, List.of());
    }
}
