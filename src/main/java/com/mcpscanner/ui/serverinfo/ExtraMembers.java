package com.mcpscanner.ui.serverinfo;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

final class ExtraMembers {

    private ExtraMembers() {
    }

    static List<CapabilityLine> of(JsonNode capability, Set<String> known) {
        return capability.properties().stream()
                .filter(member -> !known.contains(member.getKey()))
                .map(ExtraMembers::line)
                .toList();
    }

    private static CapabilityLine line(Map.Entry<String, JsonNode> member) {
        JsonNode value = member.getValue();
        return value.isBoolean() ? CapabilityLine.flag(member.getKey(), value.booleanValue())
                : CapabilityLine.member(member);
    }
}
