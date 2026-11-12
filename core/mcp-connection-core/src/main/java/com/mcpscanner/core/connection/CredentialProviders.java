package com.mcpscanner.core.connection;

public final class CredentialProviders {
    private CredentialProviders() {}
    public static CredentialProvider bearer(String token) {
        if (token == null || token.isEmpty() || token.chars().anyMatch(c -> c <= 32 || c > 126))
            throw new IllegalArgumentException("Invalid bearer token");
        return custom(java.util.Map.of("Authorization", "Bearer " + token));
    }
    public static CredentialProvider none() { return java.util.Map::of; }
    public static CredentialProvider custom(java.util.Map<String, String> headers) {
        return new HeaderCredentials(headers);
    }
}
