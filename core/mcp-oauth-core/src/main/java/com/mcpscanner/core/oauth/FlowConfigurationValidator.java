package com.mcpscanner.core.oauth;

import java.net.URI;

final class FlowConfigurationValidator {
    void validate(AuthorizationCodeConfig config) {
        try {
            new RegistrationSelector().select(new RegistrationRequest(config.issuer(), config.client(), null,
                    new AuthorizationServerCapabilities(false, null), config.callbackUri(), RegistrationMode.PRE_REGISTERED));
            new TokenEndpointRequestFactory().validate(config.client(), config.tokenEndpointAuthMethod());
            validateLocation(config.issuer());
            validateLocation(config.resource());
            validateLocation(config.authorizationEndpoint());
            validateLocation(config.tokenEndpoint());
            validateLocation(config.callbackUri());
            if (config.callbackUri().getPort() == 0 || config.scopes().stream().anyMatch(scope -> !validScope(scope))) throw invalid();
        } catch (RuntimeException failure) { throw invalid(); }
    }

    private void validateLocation(URI uri) {
        if (!OAuthDiscoveryPolicy.validLocation(uri)) throw invalid();
    }

    private boolean validScope(String scope) {
        return !scope.isEmpty() && scope.chars().allMatch(character -> character == 0x21 || character >= 0x23 && character <= 0x5b
                || character >= 0x5d && character <= 0x7e);
    }

    private OAuthFlowException invalid() {
        return new OAuthFlowException(OAuthFlowException.Reason.INVALID_CONFIGURATION, "OAuth flow configuration is invalid");
    }
}
