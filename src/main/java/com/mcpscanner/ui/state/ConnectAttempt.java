package com.mcpscanner.ui.state;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.oauth.OAuthClientHints;
import com.mcpscanner.client.TransportType;

import java.net.URI;

public record ConnectAttempt(String endpoint,
                             TransportType transport,
                             String host,
                             String authType,
                             AuthStrategy snapshotAuth,
                             OAuthClientHints oauthHints,
                             URI mcpResource,
                             com.mcpscanner.auth.grants.OptionalGrantSettings optionalGrantSettings,
                             String oauthSettingsError,
                             String identityId) {

    public static ConnectAttempt withSnapshot(String endpoint, TransportType transport, String host,
                                              String authType, AuthStrategy snapshotAuth) {
        return new ConnectAttempt(endpoint, transport, host, authType, snapshotAuth, null, null, null, null, null);
    }

    public static ConnectAttempt withOptionalGrant(String endpoint, TransportType transport, String host,
                                                   String authType,
                                                   com.mcpscanner.auth.grants.OptionalGrantSettings settings) {
        return new ConnectAttempt(endpoint, transport, host, authType, null, null, null, settings, null, null);
    }

    public static ConnectAttempt withAuto(String endpoint, TransportType transport, String host,
                                          String authType, OAuthClientHints hints, URI mcpResource,
                                          String oauthSettingsError) {
        return new ConnectAttempt(endpoint, transport, host, authType, null, hints, mcpResource,
                null, oauthSettingsError, null);
    }

    public ConnectAttempt forIdentity(String id) {
        return new ConnectAttempt(endpoint, transport, host, authType, snapshotAuth, oauthHints, mcpResource,
                optionalGrantSettings, oauthSettingsError, id);
    }

    public boolean isOptionalGrant() {
        return optionalGrantSettings != null;
    }

    @Override public String toString() { return "ConnectAttempt[redacted]"; }

    public static ConnectAttempt withOauth(String endpoint, TransportType transport, String host,
                                           String authType, OAuthClientHints hints, URI mcpResource) {
        return new ConnectAttempt(endpoint, transport, host, authType, null, hints, mcpResource, null, null, null);
    }
}

