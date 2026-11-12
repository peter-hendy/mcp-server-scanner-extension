package com.mcpscanner.auth.grant;

import java.util.Locale;
import java.util.Set;

public enum ClientType {
    PUBLIC,
    CONFIDENTIAL_CONFIGURED,
    CONFIDENTIAL_REGISTERED;

    private static final Set<String> SECRET_AUTH_METHODS = Set.of("client_secret_basic", "client_secret_post");

    public static ClientType ofRegistration(String returnedSecret, String registeredAuthMethod) {
        if (isBlank(returnedSecret) || registeredAuthMethod == null) {
            return PUBLIC;
        }
        return SECRET_AUTH_METHODS.contains(registeredAuthMethod.toLowerCase(Locale.ROOT))
                ? CONFIDENTIAL_REGISTERED : PUBLIC;
    }

    public static ClientType ofConfiguredSecret(String configuredSecret) {
        return isBlank(configuredSecret) ? PUBLIC : CONFIDENTIAL_CONFIGURED;
    }

    public boolean isConfidential() {
        return this != PUBLIC;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
