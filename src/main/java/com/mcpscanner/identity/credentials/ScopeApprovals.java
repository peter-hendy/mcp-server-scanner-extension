package com.mcpscanner.identity.credentials;

import com.mcpscanner.identity.Identity;
import com.mcpscanner.core.oauth.AuthorizationApproval;

import java.net.URI;
import java.util.Set;
import java.util.function.Function;

@FunctionalInterface
public interface ScopeApprovals {

    Function<Set<String>, AuthorizationApproval> forIdentity(Identity identity, URI issuer, URI resource,
                                                             String clientId);

    static ScopeApprovals promptRequired() {
        return (identity, issuer, resource, clientId) -> scopes -> AuthorizationApproval.REQUIRE_PROMPT;
    }
}
