package com.mcpscanner.ui;

import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.ui.connect.ConnectionAttemptResult;
import com.mcpscanner.ui.state.ConnectAttempt;
import com.mcpscanner.ui.state.ConnectionStatus;

import java.util.function.Supplier;

final class ConnectionDescriber {

    private final OAuthConfigPanel oauthConfig;
    private final Supplier<McpScannerSession> session;

    ConnectionDescriber(OAuthConfigPanel oauthConfig, Supplier<McpScannerSession> session) {
        this.oauthConfig = oauthConfig;
        this.session = session;
    }

    ConnectionStatus status(ConnectAttempt attempt, ConnectionAttemptResult outcome) {
        ConnectionStatus status = oauthConfig.buildConnectedStatus(attempt.host(), outcome.result().tools().size())
                .withAuthMethod(outcome.autoResolutionLabel().orElseGet(() -> authMethodLabel(attempt)))
                .withNegotiatedProtocol(session.get().transportType(), session.get().negotiatedProtocolVersion());
        if (outcome.oauthStrategy() == null || outcome.autoResolutionLabel().isPresent()) {
            return status;
        }
        return outcome.oauthStrategy().registrationMode().map(status::withRegistrationMode).orElse(status);
    }

    ConnectionSummary summary(ConnectAttempt attempt, ConnectionAttemptResult outcome) {
        McpScannerSession current = session.get();
        return new ConnectionSummary(attempt.transport(), current.transportType(),
                current.negotiatedProtocolVersion(), current.resolvedEndpoint(),
                outcome.autoResolutionLabel().orElse(attempt.authType()), current.sessionId());
    }

    private static String authMethodLabel(ConnectAttempt attempt) {
        if (!attempt.isOptionalGrant()) {
            return attempt.authType();
        }
        return attempt.authType() + " (" + OAuthGrant.forProfile(attempt.optionalGrantSettings().profile()).label() + ")";
    }
}
