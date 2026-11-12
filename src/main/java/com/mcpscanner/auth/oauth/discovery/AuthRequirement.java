package com.mcpscanner.auth.oauth.discovery;

public sealed interface AuthRequirement {

    record NotRequired() implements AuthRequirement {
    }

    record OAuth(DiscoveredMetadata metadata) implements AuthRequirement {
    }

    record CredentialsRequired(String answer, boolean bearerExpected) implements AuthRequirement {
    }

    record Unsupported(String reason) implements AuthRequirement {
    }
}
