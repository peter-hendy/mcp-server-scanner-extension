package com.mcpscanner.checks.shared.auth.oracle;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.checks.shared.jsonrpc.JsonNodes;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SameCatalogItems implements ResultEquivalence {

    private static final Map<String, String> IDENTIFIER_BY_LIST = Map.of(
            "tools", "name",
            "prompts", "name",
            "resources", "uri",
            "resourceTemplates", "uriTemplate");

    @Override
    public boolean equivalent(JsonNode baselineResult, JsonNode candidateResult) {
        return listIn(baselineResult)
                .flatMap(list -> identifiers(baselineResult, list)
                        .filter(baseline -> identifiers(candidateResult, list).equals(Optional.of(baseline))))
                .isPresent();
    }

    private static Optional<String> listIn(JsonNode result) {
        return IDENTIFIER_BY_LIST.keySet().stream().filter(list -> result.path(list).isArray()).findFirst();
    }

    private static Optional<Set<String>> identifiers(JsonNode result, String list) {
        JsonNode items = result.path(list);
        if (!items.isArray() || items.isEmpty()) {
            return Optional.empty();
        }
        Set<String> identifiers = new HashSet<>();
        for (JsonNode item : JsonNodes.elements(items)) {
            JsonNode identifier = item.path(IDENTIFIER_BY_LIST.get(list));
            if (!identifier.isTextual()) {
                return Optional.empty();
            }
            identifiers.add(identifier.asText());
        }
        return Optional.of(identifiers);
    }
}
