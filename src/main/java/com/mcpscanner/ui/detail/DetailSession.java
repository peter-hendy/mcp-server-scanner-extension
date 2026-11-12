package com.mcpscanner.ui.detail;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record DetailSession(String protocolVersion, Map<String, Object> capabilities) {

    public DetailSession {
        protocolVersion = protocolVersion == null ? "" : protocolVersion;
        capabilities = capabilities == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(capabilities));
    }

    public static DetailSession none() {
        return new DetailSession("", Map.of());
    }

    public DetailSession withProtocolVersion(String version) {
        return new DetailSession(version, capabilities);
    }

    public DetailSession withCapabilities(Map<String, Object> advertised) {
        return new DetailSession(protocolVersion, advertised);
    }
}
