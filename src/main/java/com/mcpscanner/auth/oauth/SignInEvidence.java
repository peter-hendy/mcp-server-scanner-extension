package com.mcpscanner.auth.oauth;

import java.util.Optional;

public record SignInEvidence(String authorizationServerMetadataJson, Optional<String> registrationResponseJson) {

    private static final SignInEvidence NONE = new SignInEvidence(null, Optional.empty());

    public static SignInEvidence none() {
        return NONE;
    }
}
