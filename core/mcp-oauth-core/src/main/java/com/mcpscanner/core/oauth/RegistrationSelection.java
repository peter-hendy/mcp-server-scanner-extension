package com.mcpscanner.core.oauth;

import java.net.URI;

public record RegistrationSelection(RegistrationMode mode, String clientId, String clientSecret, URI registrationEndpoint) {
    @Override
    public String toString() {
        return "RegistrationSelection[mode=" + mode + ", clientId=" + clientId + ", registrationEndpoint=" + registrationEndpoint + "]";
    }
}
