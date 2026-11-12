package com.mcpscanner.auth.oauth;

import com.mcpscanner.config.ExtensionConfigStore;
import com.mcpscanner.core.oauth.RegistrationMode;

import java.net.URI;
import java.time.Duration;
import java.util.List;

public record OAuthClientHints(URI issuer,
                                List<String> scopes,
                                String clientId,
                                String clientSecret,
                                boolean allowDcr,
                                int redirectPort,
                                Duration timeout,
                                RegistrationMode registrationMode,
                                URI clientMetadataUrl) {

    public static final int DEFAULT_REDIRECT_PORT = ExtensionConfigStore.DEFAULT_OAUTH_REDIRECT_PORT;
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    public OAuthClientHints(URI issuer, List<String> scopes, String clientId, String clientSecret,
                            boolean allowDcr, int redirectPort, Duration timeout) {
        this(issuer, scopes, clientId, clientSecret, allowDcr, redirectPort, timeout,
                allowDcr ? RegistrationMode.AUTO : RegistrationMode.PRE_REGISTERED, null);
    }

    public OAuthClientHints withIssuer(URI discoveredIssuer, List<String> discoveredScopes) {
        return new OAuthClientHints(discoveredIssuer, discoveredScopes, clientId, clientSecret, allowDcr, redirectPort,
                timeout, registrationMode, clientMetadataUrl);
    }

    public OAuthClientHints {
        registrationMode = registrationMode != null ? registrationMode : RegistrationMode.AUTO;
        scopes = scopes != null ? List.copyOf(scopes) : List.of();
        if (timeout == null) {
            timeout = DEFAULT_TIMEOUT;
        }
    }
}
