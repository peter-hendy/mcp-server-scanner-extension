package com.mcpscanner.checks.catalog.oauthrefreshgrant;

import java.util.Set;

record OriginalScope(Set<String> scopes, boolean declaredByAuthorizationServer) {

    OriginalScope {
        scopes = Set.copyOf(scopes);
    }
}
