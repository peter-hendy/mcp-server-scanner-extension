package com.mcpscanner.core.protocol;

import java.util.Map;
import java.util.Objects;

public record ClientProfile(ClientInfo info, String capabilitiesJson) {
    private static final String NO_CAPABILITIES = "{}";
    private static final int MAX_CAPABILITIES_BYTES = 64 * 1024;

    public ClientProfile {
        Objects.requireNonNull(info);
        capabilitiesJson = capabilitiesJson == null || capabilitiesJson.isBlank() ? NO_CAPABILITIES : capabilitiesJson.trim();
        if (!(JsonDocuments.parse(capabilitiesJson, MAX_CAPABILITIES_BYTES) instanceof Map<?, ?>))
            throw new IllegalArgumentException("capabilities object required");
    }

    public static ClientProfile of(String name, String version) {
        return new ClientProfile(ClientInfo.of(name, version), NO_CAPABILITIES);
    }

    public static ClientProfile of(ClientInfo info) { return new ClientProfile(info, NO_CAPABILITIES); }

    public String name() { return info.name(); }

    public String version() { return info.version(); }
}
