package com.mcpscanner.ui.serverinfo;

public enum CapabilityMark {
    ENABLED("✓ "),
    DISABLED("✗ "),
    VALUE(""),
    NOTE("");

    private final String prefix;

    CapabilityMark(String prefix) {
        this.prefix = prefix;
    }

    String prefix() {
        return prefix;
    }
}
