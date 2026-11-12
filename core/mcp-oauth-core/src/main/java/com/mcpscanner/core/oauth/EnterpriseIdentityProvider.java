package com.mcpscanner.core.oauth;

@FunctionalInterface
public interface EnterpriseIdentityProvider {
    TokenResponse exchange(EnterpriseIdentityRequest request);
}
