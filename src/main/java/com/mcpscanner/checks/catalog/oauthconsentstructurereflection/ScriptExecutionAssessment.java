package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

record ScriptExecutionAssessment(ScriptExecution verdict, String summary) {

    static ScriptExecutionAssessment of(List<String> enforcedPolicies) {
        List<ContentSecurityPolicy> policies = ContentSecurityPolicy.parseAll(enforcedPolicies);
        if (policies.isEmpty()) {
            return new ScriptExecutionAssessment(ScriptExecution.NO_POLICY,
                    "No enforced Content-Security-Policy was present on the page.");
        }
        ScriptExecution strictest = policies.stream().map(ContentSecurityPolicy::scriptExecution)
                .max(Comparator.naturalOrder()).orElseThrow();
        return new ScriptExecutionAssessment(strictest, describe(strictest, quoted(policies)));
    }

    private static String describe(ScriptExecution verdict, String policies) {
        return switch (verdict) {
            case BLOCKED -> "The enforced Content-Security-Policy " + policies + " allows no inline script or "
                    + "event handlers and no wildcard, scheme or listed third-party script hosts, and restricts "
                    + "base-uri wherever it relies on nonces or hashes. Same-origin script gadgets were not assessed.";
            case UNDETERMINED -> "The enforced Content-Security-Policy " + policies + " blocks inline script, "
                    + "but it allows listed script hosts or relies on nonces or hashes without restricting "
                    + "base-uri. Whether those allow attacker-chosen script was not assessed.";
            case NOT_BLOCKED -> "The enforced Content-Security-Policy " + policies + " does not block inline "
                    + "script, inline event handlers or script from arbitrary hosts.";
            case NO_POLICY -> throw new IllegalStateException("a parsed policy always has a verdict");
        };
    }

    private static String quoted(List<ContentSecurityPolicy> policies) {
        return policies.stream().map(policy -> "\"" + policy.text() + "\"").collect(Collectors.joining(", "));
    }
}
