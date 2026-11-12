package com.mcpscanner.auth.oauth;

import java.net.URI;
import java.util.Optional;
import com.mcpscanner.core.oauth.RegistrationMode;

public record OAuthSession(
        OAuthTokens tokens,
        String clientId,
        String clientSecret,
        Optional<String> registrationAccessToken,
        Optional<String> registrationClientUri,
        SharedOAuthSession sharedSession,
        RegistrationMode registrationMode,
        Optional<URI> registrationEndpoint) {

    public OAuthSession(OAuthTokens tokens, String clientId, String clientSecret,
                        Optional<String> registrationAccessToken, Optional<String> registrationClientUri,
                        SharedOAuthSession sharedSession) {
        this(tokens, clientId, clientSecret, registrationAccessToken, registrationClientUri,
                sharedSession, null, Optional.empty());
    }
}
