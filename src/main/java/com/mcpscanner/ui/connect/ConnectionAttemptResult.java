package com.mcpscanner.ui.connect;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.OAuthAuthCodeStrategy;
import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.scan.request.AdvertisedAuthorizationServer;

import java.util.Optional;

public record ConnectionAttemptResult(ConnectResult result, OAuthAuthCodeStrategy oauthStrategy,
                                      AuthStrategy resolvedAuth, String autoResolution,
                                      AdvertisedAuthorizationServer authorizationServer) {
    public ConnectionAttemptResult {
        authorizationServer = authorizationServer == null ? AdvertisedAuthorizationServer.none() : authorizationServer;
    }

    public ConnectionAttemptResult(ConnectResult result, OAuthAuthCodeStrategy oauthStrategy) {
        this(result, oauthStrategy, oauthStrategy, null, null);
    }

    public ConnectionAttemptResult(ConnectResult result, OAuthAuthCodeStrategy oauthStrategy, AuthStrategy resolvedAuth) {
        this(result, oauthStrategy, resolvedAuth, null, null);
    }

    public Optional<String> autoResolutionLabel() {
        return Optional.ofNullable(autoResolution);
    }
}
