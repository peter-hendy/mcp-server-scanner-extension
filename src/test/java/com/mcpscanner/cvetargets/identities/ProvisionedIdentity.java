package com.mcpscanner.cvetargets.identities;

import java.util.Map;

public record ProvisionedIdentity(Map<String, String> headers, Map<String, String> ownedObjects) {

    public ProvisionedIdentity {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        ownedObjects = ownedObjects == null ? Map.of() : Map.copyOf(ownedObjects);
    }
}
