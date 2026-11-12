package com.mcpscanner.ui.serverinfo;

import com.mcpscanner.core.protocol.McpRequests;

import java.util.Comparator;
import java.util.stream.Stream;

public final class SupportedProtocolVersions {

    private SupportedProtocolVersions() {
    }

    public static String newest() {
        return Stream.concat(Stream.of(McpRequests.MODERN_VERSION), McpRequests.supportedInitializeVersions().stream())
                .max(Comparator.naturalOrder())
                .orElseThrow();
    }

    public static boolean isLatest(String version) {
        return newest().equals(version);
    }
}
