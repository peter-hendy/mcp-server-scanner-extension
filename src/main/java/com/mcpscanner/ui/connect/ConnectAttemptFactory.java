package com.mcpscanner.ui.connect;

import com.mcpscanner.config.AuthTypeNames;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.oauth.OAuthClientHints;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.ui.state.ConnectAttempt;
import com.mcpscanner.ui.state.ConnectPhase;
import com.mcpscanner.ui.state.UiAction;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

public final class ConnectAttemptFactory {

    private final ConnectAuthSource auth;

    public ConnectAttemptFactory(ConnectAuthSource auth) {
        this.auth = Objects.requireNonNull(auth);
    }

    public UiAction attemptFor(String rawEndpoint, TransportType transport, String authType) {
        String endpoint = rawEndpoint.trim();
        if (endpoint.isEmpty()) {
            return rejected(ConnectPhase.CONNECT, "endpoint URL is required");
        }
        if (!EndpointUrls.hasHttpScheme(endpoint)) {
            return rejected(ConnectPhase.CONNECT, "Endpoint must use http or https");
        }
        Target target = new Target(endpoint, transport, EndpointUrls.hostOr(endpoint, endpoint), authType);
        return usesOAuthFlow(authType) ? oauthAttempt(target) : credentialAttempt(target);
    }

    private boolean usesOAuthFlow(String authType) {
        return AuthTypeNames.AUTO.equals(authType)
                || AuthTypeNames.OAUTH.equals(authType) && !auth.isOptionalGrantSelected();
    }

    private UiAction oauthAttempt(Target target) {
        boolean automatic = AuthTypeNames.AUTO.equals(target.authType());
        if (!automatic) {
            Optional<AuthStrategy> session = auth.signedInSession();
            if (session.isPresent()) {
                return requested(target.snapshot(session.get()));
            }
        }
        URI resource = URI.create(target.endpoint());
        try {
            OAuthClientHints hints = auth.oauthHints();
            return requested(automatic
                    ? target.auto(hints, resource, null)
                    : ConnectAttempt.withOauth(target.endpoint(), target.transport(), target.host(),
                            target.authType(), hints, resource));
        } catch (IllegalArgumentException ex) {
            return automatic
                    ? requested(target.auto(null, resource, ex.getMessage()))
                    : rejected(ConnectPhase.OAUTH, ex.getMessage());
        }
    }

    private UiAction credentialAttempt(Target target) {
        try {
            return requested(auth.isOptionalGrantSelected()
                    ? ConnectAttempt.withOptionalGrant(target.endpoint(), target.transport(), target.host(),
                            target.authType(), auth.optionalGrantSettings())
                    : target.snapshot(auth.currentAuthStrategy()));
        } catch (IllegalArgumentException ex) {
            return rejected(ConnectPhase.CONNECT, ex.getMessage());
        }
    }

    private UiAction requested(ConnectAttempt attempt) {
        return new UiAction.ConnectRequested(attempt.forIdentity(auth.connectingUserId()));
    }

    private static UiAction rejected(ConnectPhase phase, String reason) {
        return new UiAction.ConnectRejected(phase, reason);
    }

    private record Target(String endpoint, TransportType transport, String host, String authType) {
        ConnectAttempt snapshot(AuthStrategy strategy) {
            return ConnectAttempt.withSnapshot(endpoint, transport, host, authType, strategy);
        }

        ConnectAttempt auto(OAuthClientHints hints, URI resource, String settingsError) {
            return ConnectAttempt.withAuto(endpoint, transport, host, authType, hints, resource, settingsError);
        }
    }
}
