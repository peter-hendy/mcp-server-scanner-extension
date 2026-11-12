package com.mcpscanner.cvetargets.manifest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;

public record SeedRequest(String method, ObjectNode params, String asIdentity, @JsonIgnore SeedRouting routing) {

    public SeedRequest {
        routing = routing == null ? SeedRouting.AS_PRIMARY : routing;
    }

    public SeedRequest(String method, ObjectNode params) {
        this(method, params, null, null);
    }

    public boolean sentAsIdentity() {
        return asIdentity != null;
    }

    public SeedRequest routedBy(SeedRouting identityRouting) {
        return new SeedRequest(method, params, asIdentity, identityRouting);
    }
}
