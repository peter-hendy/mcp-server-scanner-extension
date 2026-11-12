package com.mcpscanner.identity.credentials;

import com.mcpscanner.auth.oauth.OAuthClientHints;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.identity.IdentityOAuthSettings;

import java.net.URI;
import java.util.List;
import java.util.Optional;

final class CredentialRequirements {

    private CredentialRequirements() {
    }

    static CredentialRequirement of(Identity identity, Optional<IdentityAuthKind> detected,
                                    SharedAuthSettings shared) {
        Optional<OAuthClientHints> sharedClient = shared.oauthClient();
        return switch (identity.authKind()) {
            case DETECT -> detected.filter(IdentityAuthKind.OAUTH::equals).isPresent()
                    ? browserSignIn(ownSignInClient(identity).or(() -> sharedClient))
                    : CredentialRequirement.NoCredentials.INSTANCE;
            case NONE -> CredentialRequirement.NoCredentials.INSTANCE;
            case BEARER -> CredentialRequirement.TokenRequired.INSTANCE;
            case CUSTOM_HEADERS -> headerValues(identity.headerNames());
            case OAUTH -> browserSignIn(ownSignInClient(identity).or(() -> sharedClient));
            case OAUTH_CLIENT_CREDENTIALS, OAUTH_ENTERPRISE_MANAGED -> grantAtConnect(shared.grantBlocker(identity.authKind()));
        };
    }

    private static CredentialRequirement grantAtConnect(Optional<String> blocker) {
        return blocker.<CredentialRequirement>map(CredentialRequirement.Unavailable::new)
                .orElse(CredentialRequirement.GrantAtConnect.INSTANCE);
    }

    private static CredentialRequirement headerValues(List<String> names) {
        return names.isEmpty()
                ? new CredentialRequirement.Unavailable("Name at least one header")
                : new CredentialRequirement.HeaderValuesRequired(names);
    }

    private static CredentialRequirement browserSignIn(Optional<OAuthClientHints> client) {
        return client
                .<CredentialRequirement>map(CredentialRequirement.BrowserSignInRequired::new)
                .orElseGet(() -> new CredentialRequirement.Unavailable(
                        "Enter a valid OAuth issuer URL, or complete the OAuth client in the Client tab"));
    }

    private static Optional<OAuthClientHints> ownSignInClient(Identity identity) {
        return ownOAuthHints(identity.oauth());
    }

    private static Optional<OAuthClientHints> ownOAuthHints(IdentityOAuthSettings settings) {
        if (settings.issuer().isBlank()) {
            return Optional.empty();
        }
        try {
            String clientId = settings.clientId().isBlank() ? null : settings.clientId().strip();
            return Optional.of(new OAuthClientHints(URI.create(settings.issuer().strip()), settings.scopes(), clientId,
                    null, clientId == null, OAuthClientHints.DEFAULT_REDIRECT_PORT, OAuthClientHints.DEFAULT_TIMEOUT));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }
}
