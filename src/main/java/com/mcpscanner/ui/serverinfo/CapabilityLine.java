package com.mcpscanner.ui.serverinfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.ui.detail.JsonValueText;

import java.util.Map;

public record CapabilityLine(String text, CapabilityMark mark) {

    static CapabilityLine flag(String label, boolean enabled) {
        return new CapabilityLine(label, enabled ? CapabilityMark.ENABLED : CapabilityMark.DISABLED);
    }

    static CapabilityLine value(String text) {
        return new CapabilityLine(text, CapabilityMark.VALUE);
    }

    static CapabilityLine note(String text) {
        return new CapabilityLine(text, CapabilityMark.NOTE);
    }

    static CapabilityLine member(Map.Entry<String, JsonNode> member) {
        return value(member.getKey() + ": " + JsonValueText.of(member.getValue()));
    }

    public String shownText() {
        return mark.prefix() + text;
    }
}
