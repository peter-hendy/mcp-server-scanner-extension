package com.mcpscanner.auth.grants;

import com.mcpscanner.core.oauth.ClientCredentialsAuthentication;
import java.util.Set;
import java.net.URI;

public record OptionalGrantSettings(Profile profile, URI expectedIssuer, String clientId, String clientSecret,
                                    Set<String> scopes, ClientCredentialsAuthentication authentication,
                                    String enterpriseProviderId) {
    public enum Profile { CLIENT_CREDENTIALS, ENTERPRISE_MANAGED }
    public OptionalGrantSettings {
        if (profile == null || clientId == null || clientId.isBlank() || scopes == null || authentication == null) {
            throw new com.mcpscanner.core.oauth.OAuthFlowException(
                    com.mcpscanner.core.oauth.OAuthFlowException.Reason.INVALID_CONFIGURATION,
                    "Select a grant profile and configure its client authentication");
        }
        validateExpectedIssuer(expectedIssuer);
        scopes = Set.copyOf(scopes);
    }
    private static void validateExpectedIssuer(URI issuer) {
        try {
            new com.mcpscanner.core.oauth.OAuthDiscoveryPolicy().authorizationServerLocations(issuer);
        } catch (RuntimeException invalidIssuer) {
            throw new com.mcpscanner.core.oauth.OAuthFlowException(
                    com.mcpscanner.core.oauth.OAuthFlowException.Reason.INVALID_CONFIGURATION,
                    "Configure a valid expected authorization server issuer");
        }
    }
    @Override public String toString() { return "OptionalGrantSettings[redacted]"; }
}
