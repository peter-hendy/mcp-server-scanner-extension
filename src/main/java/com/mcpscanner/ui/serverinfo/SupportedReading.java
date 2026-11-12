package com.mcpscanner.ui.serverinfo;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class SupportedReading implements CapabilityReading {

    private static final String SUPPORTED = "supported";

    @Override
    public List<CapabilityLine> linesOf(JsonNode capability) {
        List<CapabilityLine> lines = new ArrayList<>();
        lines.add(CapabilityLine.flag(SUPPORTED, true));
        lines.addAll(ExtraMembers.of(capability, Set.of()));
        return lines;
    }
}
