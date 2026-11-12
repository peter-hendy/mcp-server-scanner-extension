package com.mcpscanner.core.oauth;

public record EnterpriseManagedConfig(CredentialBinding binding, ProtectedResourceMetadata resourceMetadata,
        EnterpriseAuthorizationMetadata serverMetadata, RegisteredClient client,
        ClientCredentialsAuthentication authentication, boolean includeResource) {
    @Override public String toString() { return "EnterpriseManagedConfig[redacted]"; }
}
