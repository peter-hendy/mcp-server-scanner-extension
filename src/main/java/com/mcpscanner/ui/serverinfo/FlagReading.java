package com.mcpscanner.ui.serverinfo;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FlagReading implements CapabilityReading {

    private final Map<String, String> labelsByFlag;

    private FlagReading(Map<String, String> labelsByFlag) {
        this.labelsByFlag = labelsByFlag;
    }

    static FlagReading of(String... flagLabelPairs) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (int index = 0; index + 1 < flagLabelPairs.length; index += 2) {
            labels.put(flagLabelPairs[index], flagLabelPairs[index + 1]);
        }
        return new FlagReading(labels);
    }

    @Override
    public List<CapabilityLine> linesOf(JsonNode capability) {
        List<CapabilityLine> lines = new ArrayList<>();
        labelsByFlag.forEach((flag, label) ->
                lines.add(CapabilityLine.flag(label, capability.path(flag).booleanValue())));
        lines.addAll(ExtraMembers.of(capability, labelsByFlag.keySet()));
        return lines;
    }
}
