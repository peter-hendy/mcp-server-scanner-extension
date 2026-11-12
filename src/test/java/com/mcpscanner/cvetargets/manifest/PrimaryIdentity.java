package com.mcpscanner.cvetargets.manifest;

import java.util.Map;

public record PrimaryIdentity(String name, String privateMarker, Map<String, String> ownedObjects) {

    public static final String DEFAULT_NAME = "primary";

    public PrimaryIdentity {
        name = name == null ? DEFAULT_NAME : name;
        ownedObjects = ownedObjects == null ? Map.of() : Map.copyOf(ownedObjects);
    }

    public static PrimaryIdentity unnamed() {
        return new PrimaryIdentity(null, null, null);
    }
}
