package com.mcpscanner.cvetargets.identities;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public record ArmSetupReport(Map<String, ItemListing> listings, JsonNode groundTruth, List<String> contradictions) {

    public ArmSetupReport {
        listings = listings == null ? Map.of() : Map.copyOf(listings);
        contradictions = contradictions == null ? List.of() : List.copyOf(contradictions);
    }
}
