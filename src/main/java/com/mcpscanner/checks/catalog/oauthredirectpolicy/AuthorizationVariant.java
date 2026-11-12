package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import java.util.Map;

enum AuthorizationVariant {
    STANDARD("standard authorization request", "code", Map.of()),
    FRAGMENT("response_mode=fragment", "code", Map.of("response_mode", "fragment")),
    SILENT("prompt=none", "code", Map.of("prompt", "none")),
    UNSUPPORTED_RESPONSE_TYPE("unsupported response_type", "scancheck_unsupported", Map.of()),
    CONSENT_DENIAL("denied consent form post", "code", Map.of("allow", "false", "decision", "deny"));

    private final String label;
    private final String responseType;
    private final Map<String, String> extraParameters;

    AuthorizationVariant(String label, String responseType, Map<String, String> extraParameters) {
        this.label = label;
        this.responseType = responseType;
        this.extraParameters = extraParameters;
    }

    boolean submitsForm() {
        return this == CONSENT_DENIAL;
    }

    String label() {
        return label;
    }

    String responseType() {
        return responseType;
    }

    Map<String, String> extraParameters() {
        return extraParameters;
    }
}
