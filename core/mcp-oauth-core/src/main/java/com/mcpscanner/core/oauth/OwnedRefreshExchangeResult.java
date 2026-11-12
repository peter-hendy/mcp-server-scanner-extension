package com.mcpscanner.core.oauth;

import java.util.Set;

public record OwnedRefreshExchangeResult(int status, boolean validTokenResponse,
        Set<String> grantedScopes, boolean protectedOperationVerified) {
    public OwnedRefreshExchangeResult { grantedScopes = Set.copyOf(grantedScopes); }
}
