package com.mcpscanner.auth;

import com.mcpscanner.core.oauth.CredentialBinding;
import com.mcpscanner.core.oauth.OAuthDiscoveryPolicy;
import com.mcpscanner.core.oauth.OAuthFlowException;

import java.net.URI;
import java.util.Optional;

final class EndpointCredentialBinding {
    private final URI intendedEndpoint;

    EndpointCredentialBinding(URI intendedEndpoint, CredentialBinding binding) {
        this.intendedEndpoint = intendedEndpoint;
        if (boundResourceCoversEndpoint(intendedEndpoint, binding)) {
            return;
        }
        throw invalidEndpoint();
    }

    private static boolean boundResourceCoversEndpoint(URI endpoint, CredentialBinding binding) {
        try {
            boolean exactEndpoint = new OAuthDiscoveryPolicy().protectedResourceCandidates(endpoint, Optional.empty()).stream()
                    .flatMap(candidate -> candidate.expectedResources().stream())
                    .anyMatch(resource -> resource.toString().equals(binding.resource().toString()));
            return exactEndpoint || binding.resource().toString().equals(originOf(endpoint));
        } catch (RuntimeException invalidEndpoint) {
            return false;
        }
    }

    private static String originOf(URI endpoint) {
        return endpoint.getScheme() + "://" + endpoint.getRawAuthority();
    }

    void validate(URI endpoint) {
        if (!intendedEndpoint.equals(endpoint)) {
            throw invalidEndpoint();
        }
    }

    private static OAuthFlowException invalidEndpoint() {
        return new OAuthFlowException(OAuthFlowException.Reason.INVALID_CONFIGURATION,
                "MCP endpoint does not match the credential configuration");
    }
}
