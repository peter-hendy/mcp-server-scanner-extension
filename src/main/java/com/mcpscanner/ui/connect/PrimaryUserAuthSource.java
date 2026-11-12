package com.mcpscanner.ui.connect;

import com.mcpscanner.config.AuthTypeNames;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.grants.OptionalGrantSettings;
import com.mcpscanner.auth.oauth.OAuthClientHints;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.identity.credentials.CredentialNeed;
import com.mcpscanner.identity.credentials.CredentialNeedException;
import com.mcpscanner.identity.credentials.PrimaryUserCredentials;
import com.mcpscanner.ui.Validated;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class PrimaryUserAuthSource implements ConnectAuthSource {

    private final ServerAuthSettings server;
    private final PrimaryUserCredentials primary;

    public PrimaryUserAuthSource(ServerAuthSettings server, PrimaryUserCredentials primary) {
        this.server = Objects.requireNonNull(server, "server");
        this.primary = Objects.requireNonNull(primary, "primary");
    }

    public String authType() {
        if (primary.autoDetects()) {
            return AuthTypeNames.AUTO;
        }
        return authTypeOf(primary.user().authKind());
    }

    public Optional<String> connectBlocker() {
        Optional<OptionalGrantSettings.Profile> grant = grantProfile();
        if (grant.isPresent()) {
            return server.optionalGrantBlocker(grant.get());
        }
        return switch (authType()) {
            case AuthTypeNames.AUTO, AuthTypeNames.NONE -> Optional.empty();
            case AuthTypeNames.OAUTH -> signedInSession().isPresent() ? Optional.empty()
                    : refusalOf(this::oauthHints);
            default -> refusalOf(this::currentAuthStrategy);
        };
    }

    public Optional<CredentialNeed> connectNeed() {
        if (isOptionalGrantSelected()) {
            return Optional.empty();
        }
        return switch (authType()) {
            case AuthTypeNames.AUTO, AuthTypeNames.NONE -> Optional.empty();
            case AuthTypeNames.OAUTH -> signedInSession().isPresent() ? Optional.empty() : oauthNeed();
            default -> credentialNeed();
        };
    }

    private Optional<CredentialNeed> oauthNeed() {
        return refusalOf(this::oauthHints).map(CredentialNeed.OAuthSignInIncomplete::new);
    }

    private Optional<CredentialNeed> credentialNeed() {
        try {
            currentAuthStrategy();
            return Optional.empty();
        } catch (CredentialNeedException needed) {
            return Optional.of(needed.need());
        }
    }

    @Override
    public boolean isOptionalGrantSelected() {
        return grantProfile().isPresent();
    }

    @Override
    public OAuthClientHints oauthHints() {
        return primary.signInClient().orElseGet(server::oauthHints);
    }

    @Override
    public OptionalGrantSettings optionalGrantSettings() {
        return server.optionalGrantSettings(grantProfile().orElseThrow(() ->
                new IllegalArgumentException("Set " + primary.user().name() + " to an OAuth grant first")));
    }

    private Optional<OptionalGrantSettings.Profile> grantProfile() {
        return OAuthGrantKinds.profileOf(primary.user().authKind());
    }

    @Override
    public AuthStrategy currentAuthStrategy() {
        return primary.connectionCredential();
    }

    @Override
    public String connectingUserId() {
        return primary.user().id();
    }

    @Override
    public Optional<AuthStrategy> signedInSession() {
        return primary.liveCredential();
    }

    private static Optional<String> refusalOf(Supplier<?> attempt) {
        return Validated.of(attempt).problem();
    }

    private static String authTypeOf(IdentityAuthKind kind) {
        return switch (kind) {
            case NONE -> AuthTypeNames.NONE;
            case DETECT, OAUTH, OAUTH_CLIENT_CREDENTIALS, OAUTH_ENTERPRISE_MANAGED -> AuthTypeNames.OAUTH;
            case BEARER -> AuthTypeNames.BEARER;
            case CUSTOM_HEADERS -> AuthTypeNames.CUSTOM_HEADERS;
        };
    }
}
