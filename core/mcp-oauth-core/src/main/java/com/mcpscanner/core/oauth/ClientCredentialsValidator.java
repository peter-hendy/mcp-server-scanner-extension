package com.mcpscanner.core.oauth;

final class ClientCredentialsValidator {
    void validate(ClientCredentialsConfig config) {
        try {
            var binding = config.binding();
            var server = config.serverMetadata();
            var client = config.client();
            if (binding.grantMode() != OAuthGrantMode.CLIENT_CREDENTIALS || !OAuthDiscoveryPolicy.validLocation(binding.issuer()) || binding.issuer().getRawQuery() != null
                    || !OAuthDiscoveryPolicy.validLocation(binding.resource()) || !OAuthDiscoveryPolicy.validLocation(server.tokenEndpoint())
                    || !binding.issuer().toString().equals(server.issuer().toString())
                    || !binding.issuer().toString().equals(client.issuer().toString())
                    || !binding.resource().toString().equals(config.resourceMetadata().resource().toString())
                    || config.resourceMetadata().issuers().stream().noneMatch(issuer -> issuer.toString().equals(binding.issuer().toString()))
                    || binding.clientId().isBlank() || !binding.clientId().equals(client.clientId())
                    || binding.scopes().stream().anyMatch(scope -> !ScopeValues.valid(scope))
                    || !server.grantTypes().contains("client_credentials")
                    || !server.authenticationMethods().contains(config.authentication().method())) throw invalid();
            validateAuthentication(config);
        } catch (RuntimeException failure) { throw invalid(); }
    }

    private void validateAuthentication(ClientCredentialsConfig config) {
        new TokenEndpointRequestFactory().validate(config.client(), config.authentication(),
                config.serverMetadata().authenticationMethods(), config.serverMetadata().signingAlgorithms(), false);
    }

    private OAuthFlowException invalid() {
        return new OAuthFlowException(OAuthFlowException.Reason.INVALID_CONFIGURATION, "Client credentials configuration is invalid");
    }
}
