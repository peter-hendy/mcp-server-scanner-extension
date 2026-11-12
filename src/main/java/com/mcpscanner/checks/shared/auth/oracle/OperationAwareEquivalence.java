package com.mcpscanner.checks.shared.auth.oracle;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public final class OperationAwareEquivalence implements ResultEquivalence {

    private static final List<String> OPERATION_PAYLOAD_KEYS = List.of("content", "contents", "messages");

    private final ResultEquivalence operationResults = new SameResultValue();
    private final ResultEquivalence otherResults = new SameResultShape();

    @Override
    public boolean equivalent(JsonNode baselineResult, JsonNode candidateResult) {
        return carriesOperationPayload(baselineResult)
                ? operationResults.equivalent(baselineResult, candidateResult)
                : otherResults.equivalent(baselineResult, candidateResult);
    }

    private static boolean carriesOperationPayload(JsonNode result) {
        return OPERATION_PAYLOAD_KEYS.stream().anyMatch(key -> result.path(key).isArray());
    }
}
