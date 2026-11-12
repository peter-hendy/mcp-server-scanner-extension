package com.mcpscanner.core.oauth;

public record ClientCredentialsConfig(CredentialBinding binding, ProtectedResourceMetadata resourceMetadata,
        ClientCredentialsMetadata serverMetadata, RegisteredClient client, ClientCredentialsAuthentication authentication) {
    @Override public String toString() { return "ClientCredentialsConfig[redacted]"; }
}
