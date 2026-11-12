package com.mcpscanner.ui.detail;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;
import java.util.stream.Stream;

public final class DeclaredTitle {

    private DeclaredTitle() {
    }

    public static Optional<String> firstOf(JsonNode... candidates) {
        return Stream.of(candidates)
                .filter(candidate -> candidate != null && candidate.isTextual() && !candidate.asText().isBlank())
                .map(JsonNode::asText)
                .findFirst();
    }
}
