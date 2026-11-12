package com.mcpscanner.ui.serverinfo;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

final class LeafPathReading implements CapabilityReading {

    private static final String REQUESTS = "requests";
    private static final String METHOD_SEPARATOR = "/";

    @Override
    public List<CapabilityLine> linesOf(JsonNode capability) {
        List<CapabilityLine> lines = new ArrayList<>();
        collect(capability, List.of(), lines);
        return lines;
    }

    private static void collect(JsonNode node, List<String> path, List<CapabilityLine> lines) {
        node.properties().forEach(member -> {
            List<String> memberPath = appended(path, member.getKey());
            JsonNode value = member.getValue();
            if (value.isObject() && !value.isEmpty()) {
                collect(value, memberPath, lines);
            } else {
                lines.add(CapabilityLine.flag(label(memberPath), !value.isBoolean() || value.booleanValue()));
            }
        });
    }

    private static List<String> appended(List<String> path, String key) {
        List<String> extended = new ArrayList<>(path);
        extended.add(key);
        return extended;
    }

    private static String label(List<String> path) {
        boolean underRequests = path.size() > 1 && REQUESTS.equals(path.get(0));
        return String.join(METHOD_SEPARATOR, underRequests ? path.subList(1, path.size()) : path);
    }
}
