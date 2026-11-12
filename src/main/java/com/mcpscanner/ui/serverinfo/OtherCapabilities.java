package com.mcpscanner.ui.serverinfo;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

final class OtherCapabilities {

    private static final String TITLE = "Other";
    private static final String NONE = "None";

    private OtherCapabilities() {
    }

    static CapabilityCard cardFrom(JsonNode capabilities) {
        List<CapabilityLine> unknown = capabilities.properties().stream()
                .filter(member -> !KnownCapability.KEYS.contains(member.getKey()))
                .map(CapabilityLine::member)
                .toList();
        return unknown.isEmpty() ? CapabilityCard.absent(TITLE, NONE) : CapabilityCard.advertised(TITLE, unknown);
    }
}
