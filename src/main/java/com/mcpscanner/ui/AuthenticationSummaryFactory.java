package com.mcpscanner.ui;

import com.mcpscanner.auth.OAuthAuthCodeStrategy;
import com.mcpscanner.auth.grants.OptionalGrantSettings;
import com.mcpscanner.auth.oauth.AuthState;
import com.mcpscanner.ui.connect.ConnectionAttemptResult;
import com.mcpscanner.ui.state.ConnectAttempt;

import java.net.URI;
import java.util.Optional;
import java.util.function.Function;

final class AuthenticationSummaryFactory {

    private static final String SEPARATOR = " · ";

    private AuthenticationSummaryFactory() {
    }

    static AuthenticationSummary from(ConnectAttempt attempt, ConnectionAttemptResult outcome, String user,
                                      Function<URI, Optional<DcrRegistrations.Registration>> registrations) {
        return builder(attempt, outcome, registrations).connectedAs(user).build();
    }

    private static AuthenticationSummary.Builder builder(ConnectAttempt attempt, ConnectionAttemptResult outcome,
                                                         Function<URI, Optional<DcrRegistrations.Registration>> registrations) {
        if (outcome.oauthStrategy() != null) {
            String method = outcome.autoResolutionLabel()
                    .orElseGet(() -> method(attempt, OAuthGrant.AUTHORIZATION_CODE));
            return authorizationCode(method, outcome.oauthStrategy())
                    .clientManagement(registrations.apply(outcome.oauthStrategy().issuer()));
        }
        if (attempt.isOptionalGrant()) {
            return optionalGrant(attempt, outcome);
        }
        return AuthenticationSummary.builder(outcome.autoResolutionLabel().orElse(attempt.authType()))
                .credential(outcome.resolvedAuth());
    }

    private static AuthenticationSummary.Builder authorizationCode(String method, OAuthAuthCodeStrategy strategy) {
        return AuthenticationSummary.builder(method)
                .issuer(strategy.issuer())
                .clientId(strategy.clientId())
                .registration(strategy.registrationMode().map(RegistrationModeText::label).orElse(null))
                .requestedScopes(strategy.requestedScopes())
                .grantedScopes(strategy.grantedScopes())
                .tokenExpiry(() -> Optional.ofNullable(strategy.snapshot()).map(AuthState::expiresAt))
                .credential(strategy);
    }

    private static AuthenticationSummary.Builder optionalGrant(ConnectAttempt attempt,
                                                               ConnectionAttemptResult outcome) {
        OptionalGrantSettings settings = attempt.optionalGrantSettings();
        return AuthenticationSummary.builder(method(attempt, OAuthGrant.forProfile(settings.profile())))
                .issuer(settings.expectedIssuer())
                .clientId(settings.clientId())
                .requestedScopes(settings.scopes().stream().sorted().toList())
                .credential(outcome.resolvedAuth());
    }

    private static String method(ConnectAttempt attempt, OAuthGrant grant) {
        return attempt.authType() + SEPARATOR + grant.label();
    }
}
