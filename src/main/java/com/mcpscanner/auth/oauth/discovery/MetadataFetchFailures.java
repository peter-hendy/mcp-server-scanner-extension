package com.mcpscanner.auth.oauth.discovery;

public record MetadataFetchFailures(String protectedResourceMetadata, String wwwAuthenticateChallenge) {

    private static final MetadataFetchFailures NONE = new MetadataFetchFailures(null, null);

    public static MetadataFetchFailures none() {
        return NONE;
    }
}
