package com.mcpscanner.checks.shared.auth.oracle;

import com.fasterxml.jackson.databind.JsonNode;

@FunctionalInterface
public interface ResultEquivalence {

    boolean equivalent(JsonNode baselineResult, JsonNode candidateResult);
}
