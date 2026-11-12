package com.mcpscanner.checks.shared.auth.oracle;

import com.fasterxml.jackson.databind.JsonNode;

public final class SameResultValue implements ResultEquivalence {

    @Override
    public boolean equivalent(JsonNode baselineResult, JsonNode candidateResult) {
        return baselineResult.equals(candidateResult);
    }
}
