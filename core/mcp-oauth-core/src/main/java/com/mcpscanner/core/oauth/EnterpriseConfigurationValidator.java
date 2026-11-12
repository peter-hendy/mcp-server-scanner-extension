package com.mcpscanner.core.oauth;

final class EnterpriseConfigurationValidator {
    void validate(EnterpriseManagedConfig config) {
        try {
            var binding = config.binding();
            var server = config.serverMetadata();
            var client = config.client();
            if (binding.grantMode() != OAuthGrantMode.ENTERPRISE_MANAGED
                    || !OAuthDiscoveryPolicy.validLocation(binding.issuer()) || binding.issuer().getRawQuery() != null
                    || !OAuthDiscoveryPolicy.validLocation(binding.resource()) || !OAuthDiscoveryPolicy.validLocation(server.tokenEndpoint())
                    || !binding.issuer().toString().equals(server.issuer().toString())
                    || !binding.issuer().toString().equals(client.issuer().toString())
                    || !binding.resource().toString().equals(config.resourceMetadata().resource().toString())
                    || config.resourceMetadata().issuers().stream().noneMatch(issuer -> issuer.toString().equals(binding.issuer().toString()))
                    || binding.clientId().isBlank() || !binding.clientId().equals(client.clientId())
                    || binding.scopes().stream().anyMatch(scope -> !ScopeValues.valid(scope))
                    || !server.grantProfiles().contains("urn:ietf:params:oauth:grant-profile:id-jag")) throw invalid();
            new TokenEndpointRequestFactory().validate(client, config.authentication(), server.authenticationMethods(), server.signingAlgorithms(), true);
        } catch (RuntimeException failure) { throw invalid(); }
    }

    private OAuthFlowException invalid() {
        return new OAuthFlowException(OAuthFlowException.Reason.INVALID_CONFIGURATION, "Enterprise authorization configuration is invalid");
    }
}
