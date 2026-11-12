package com.mcpscanner.cvetargets.identities;

import com.mcpscanner.auth.AuthStrategy;

import java.util.Map;

public final class ProvisionedCredential implements AuthStrategy {

    private volatile Map<String, String> headers = Map.of();

    void provide(Map<String, String> provisioned) {
        headers = Map.copyOf(provisioned);
    }

    @Override
    public Map<String, String> headers() {
        return headers;
    }
}
