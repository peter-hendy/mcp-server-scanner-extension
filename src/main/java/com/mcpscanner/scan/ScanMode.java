package com.mcpscanner.scan;

import java.util.Arrays;
import java.util.Optional;

public enum ScanMode {
    MCP_ONLY,
    MCP_AND_BURP;

    public static Optional<ScanMode> named(String name) {
        return Arrays.stream(values()).filter(mode -> mode.name().equals(name)).findFirst();
    }
}
