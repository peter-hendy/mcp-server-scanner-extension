package com.mcpscanner.ui.serverinfo;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

final class KeyReading implements CapabilityReading {

    private static final String NONE_DECLARED = "None declared";

    @Override
    public List<CapabilityLine> linesOf(JsonNode capability) {
        if (capability.isEmpty()) {
            return List.of(CapabilityLine.note(NONE_DECLARED));
        }
        return capability.properties().stream().map(Map.Entry::getKey).map(CapabilityLine::value).toList();
    }
}
