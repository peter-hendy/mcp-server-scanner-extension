package com.mcpscanner.auth.oauth;

import com.mcpscanner.core.oauth.RegistrationMode;

import java.util.Optional;

record ClientRegistration(String clientId, String clientSecret,
                          Optional<String> registrationAccessToken,
                          Optional<String> registrationClientUri,
                          RegistrationMode registrationMode,
                          Optional<String> registrationResponseJson) {

    ClientRegistration(String clientId, String clientSecret, RegistrationMode registrationMode) {
        this(clientId, clientSecret, Optional.empty(), Optional.empty(), registrationMode, Optional.empty());
    }
}
