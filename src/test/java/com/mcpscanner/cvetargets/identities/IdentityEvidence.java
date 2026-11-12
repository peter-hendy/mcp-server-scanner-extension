package com.mcpscanner.cvetargets.identities;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record IdentityEvidence(JsonNode groundTruth, List<AccessRecord> accessOutcomes) {

    public IdentityEvidence {
        accessOutcomes = accessOutcomes == null ? List.of() : List.copyOf(accessOutcomes);
    }
}
