package com.mcpscanner.identity.credentials;

import com.mcpscanner.mcp.HttpOrigin;

import java.util.List;

public record CredentialsCleared(List<HttpOrigin> previousOrigins, HttpOrigin newOrigin, List<String> userNames) {

    public CredentialsCleared {
        previousOrigins = List.copyOf(previousOrigins);
        userNames = List.copyOf(userNames);
    }

    public String message() {
        return "Endpoint origin changed from " + String.join(" and ", previousOrigins.stream().map(String::valueOf).toList())
                + " to " + newOrigin + ": cleared the credentials of " + String.join(", ", userNames)
                + ". Enter them again in Users.";
    }
}
