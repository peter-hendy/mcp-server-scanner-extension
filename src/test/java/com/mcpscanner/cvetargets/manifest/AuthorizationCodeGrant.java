package com.mcpscanner.cvetargets.manifest;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.cvetargets.oauth.ArmBoundOAuthStrategy;

import java.util.ArrayList;
import java.util.List;

public record AuthorizationCodeGrant(String issuerPath, List<String> scopes, OAuthClientSetup client)
        implements TokenSource {

    public AuthorizationCodeGrant {
        issuerPath = issuerPath == null ? "" : issuerPath;
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }

    @Override
    public String token() {
        throw new IllegalStateException("an authorization-code grant is obtained when its arm starts");
    }

    @Override
    public List<String> violations() {
        List<String> violations = new ArrayList<>();
        if (client == null) {
            violations.add("auth.token.client is required");
        } else {
            violations.addAll(client.violations());
        }
        if (!issuerPath.isEmpty() && !issuerPath.startsWith("/")) {
            violations.add("auth.token.issuerPath must start with /");
        }
        return violations;
    }

    @Override
    public AuthStrategy strategy() {
        return new ArmBoundOAuthStrategy(this);
    }
}
