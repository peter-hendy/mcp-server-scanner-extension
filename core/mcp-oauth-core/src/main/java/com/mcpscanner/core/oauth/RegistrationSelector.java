package com.mcpscanner.core.oauth;

public final class RegistrationSelector {
    public RegistrationSelection select(RegistrationRequest request) {
        var client = request.configuredClient();
        if (allows(request, RegistrationMode.PRE_REGISTERED) && client != null) {
            if (client.issuer() == null || request.issuer() == null || !client.issuer().toString().equals(request.issuer().toString())) {
                throw new RegistrationException(RegistrationException.Reason.ISSUER_MISMATCH, "Configured client belongs to another issuer");
            }
            if (client.clientId() == null || client.clientId().isBlank()) {
                throw new RegistrationException(RegistrationException.Reason.INVALID_CLIENT, "Configured client ID is required");
            }
            return new RegistrationSelection(RegistrationMode.PRE_REGISTERED, client.clientId(), client.clientSecret(), null);
        }
        if (allows(request, RegistrationMode.CIMD) && request.cimdUrl() != null && request.metadata().cimdSupported()) {
            new CimdDocumentValidator().validateLocation(request.cimdUrl(), request.callbackUri());
            return new RegistrationSelection(RegistrationMode.CIMD, request.cimdUrl().toString(), null, null);
        }
        if (allows(request, RegistrationMode.DCR) && request.metadata().registrationEndpoint() != null) {
            return new RegistrationSelection(RegistrationMode.DCR, null, null, request.metadata().registrationEndpoint());
        }
        if (request.mode() != RegistrationMode.AUTO) {
            throw new RegistrationException(RegistrationException.Reason.UNSUPPORTED_MODE, "Selected registration mode is unavailable: " + request.mode());
        }
        throw new RegistrationException(RegistrationException.Reason.MISSING_REGISTRATION, "Configure a registered client or supported client metadata document");
    }
    private boolean allows(RegistrationRequest request, RegistrationMode mode) {
        return request.mode() == RegistrationMode.AUTO || request.mode() == mode;
    }
}
