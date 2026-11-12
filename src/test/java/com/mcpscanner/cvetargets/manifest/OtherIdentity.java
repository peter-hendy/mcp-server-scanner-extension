package com.mcpscanner.cvetargets.manifest;

import com.mcpscanner.checks.api.PrincipalRole;

import java.util.Map;

public record OtherIdentity(String id, String name, PrincipalRole role, Map<String, String> headers,
                            String privateMarker, Map<String, String> ownedObjects) {

    public OtherIdentity {
        name = name == null ? id : name;
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        ownedObjects = ownedObjects == null ? Map.of() : Map.copyOf(ownedObjects);
    }
}
