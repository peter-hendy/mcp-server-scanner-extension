package com.mcpscanner.cvetargets.manifest;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.cvetargets.identities.ProvisionedCredential;

import java.util.List;

public record ProvisionedToken() implements TokenSource {

    @Override
    public String token() {
        throw new IllegalStateException("a provisioned credential is minted when its arm starts");
    }

    @Override
    public List<String> violations() {
        return List.of();
    }

    @Override
    public AuthStrategy strategy() {
        return new ProvisionedCredential();
    }
}
