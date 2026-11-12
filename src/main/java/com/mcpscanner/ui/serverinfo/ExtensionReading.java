package com.mcpscanner.ui.serverinfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.ui.detail.JsonValueText;

import java.util.List;
import java.util.Map;

final class ExtensionReading implements CapabilityReading {

    private static final String NONE_DECLARED = "None declared";
    private static final String LABEL_SEPARATOR = " · ";

    @Override
    public List<CapabilityLine> linesOf(JsonNode capability) {
        if (capability.isEmpty()) {
            return List.of(CapabilityLine.note(NONE_DECLARED));
        }
        return capability.properties().stream().map(ExtensionReading::line).toList();
    }

    private static CapabilityLine line(Map.Entry<String, JsonNode> extension) {
        String key = extension.getKey();
        String named = ExtensionLabel.of(key).map(label -> label + LABEL_SEPARATOR + key).orElse(key);
        return CapabilityLine.value(named + ": " + JsonValueText.of(extension.getValue()));
    }
}
