package com.mcpscanner.identity.credentials;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.auth.oauth.OAuthClientHints;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.identity.IdentityRegistry;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public final class PrimaryUserCredentials {

    private final IdentityRegistry registry;
    private final IdentityCredentials credentials;
    private final Consumer<String> signedInOutsideTheQueue;

    public PrimaryUserCredentials(IdentityRegistry registry, IdentityCredentials credentials) {
        this(registry, credentials, identityId -> { });
    }

    public PrimaryUserCredentials(IdentityRegistry registry, IdentityCredentials credentials,
                                Consumer<String> signedInOutsideTheQueue) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.signedInOutsideTheQueue = Objects.requireNonNull(signedInOutsideTheQueue, "signedInOutsideTheQueue");
    }

    public Identity user() {
        return registry.primaryUser();
    }

    public Optional<AuthStrategy> liveCredential() {
        return registry.credential(user().id());
    }

    public AuthStrategy connectionCredential() {
        Identity user = user();
        if (user.authKind() == IdentityAuthKind.NONE) {
            return new NoAuthStrategy();
        }
        if (user.authKind() == IdentityAuthKind.DETECT) {
            return registry.credential(user.id()).orElseGet(NoAuthStrategy::new);
        }
        return registry.credential(user.id()).orElseThrow(() -> missing(user));
    }

    public Optional<OAuthClientHints> signInClient() {
        return credentials.requirement(user().id()) instanceof CredentialRequirement.BrowserSignInRequired required
                ? Optional.of(required.hints()) : Optional.empty();
    }

    public boolean autoDetects() {
        return user().authKind() == IdentityAuthKind.DETECT && liveCredential().isEmpty();
    }

    public void signedIn(String identityId, AuthStrategy session) {
        registry.find(identityId).ifPresent(user -> {
            if (user.authKind() == IdentityAuthKind.DETECT) {
                registry.recordDetected(user.id(), IdentityAuthKind.OAUTH);
            } else if (user.authKind() != IdentityAuthKind.OAUTH) {
                registry.update(user.withAuthKind(IdentityAuthKind.OAUTH));
            }
            registry.setCredential(user.id(), session);
            signedInOutsideTheQueue.accept(user.id());
        });
    }

    public void detectedNoAuthentication(String identityId) {
        registry.find(identityId).filter(user -> user.authKind() == IdentityAuthKind.DETECT)
                .ifPresent(user -> registry.recordDetected(user.id(), IdentityAuthKind.NONE));
    }

    public void sessionRejected() {
        registry.removeCredential(user().id());
    }

    private CredentialNeedException missing(Identity user) {
        String problem = problem(user, credentials.requirement(user.id()));
        return new CredentialNeedException(problem, new CredentialNeed.MissingCredential(user.authKind(), problem));
    }

    private static String problem(Identity user, CredentialRequirement requirement) {
        if (requirement instanceof CredentialRequirement.Unavailable unavailable) {
            return unavailable.reason();
        }
        return "Enter " + missingCredential(requirement) + " for " + user.name();
    }

    private static String missingCredential(CredentialRequirement requirement) {
        if (requirement instanceof CredentialRequirement.HeaderValuesRequired) {
            return "the header values";
        }
        if (requirement instanceof CredentialRequirement.BrowserSignInRequired) {
            return "a sign-in";
        }
        return "a bearer token";
    }
}
