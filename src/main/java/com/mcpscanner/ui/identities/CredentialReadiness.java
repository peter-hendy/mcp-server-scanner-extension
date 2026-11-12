package com.mcpscanner.ui.identities;

import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.credentials.CredentialRequirement;

import java.util.Optional;

final class CredentialReadiness {

    private final IdentityUiServices services;

    CredentialReadiness(IdentityUiServices services) {
        this.services = services;
    }

    boolean hasCredentials(Identity identity) {
        return holdsCredential(identity)
                || requirement(identity) instanceof CredentialRequirement.NoCredentials
                || requirement(identity) instanceof CredentialRequirement.GrantAtConnect;
    }

    Optional<String> connectAsBlocker(Identity identity) {
        if (holdsCredential(identity)) {
            return Optional.empty();
        }
        CredentialRequirement requirement = requirement(identity);
        if (requirement instanceof CredentialRequirement.Unavailable unavailable) {
            return Optional.of(unavailable.reason());
        }
        if (requirement instanceof CredentialRequirement.TokenRequired) {
            return Optional.of("Enter a bearer token for " + identity.name() + " first");
        }
        if (requirement instanceof CredentialRequirement.HeaderValuesRequired) {
            return Optional.of("Enter the header values for " + identity.name() + " first");
        }
        return Optional.empty();
    }

    private boolean holdsCredential(Identity identity) {
        return services.registry().credential(identity.id()).isPresent();
    }

    private CredentialRequirement requirement(Identity identity) {
        return services.credentials().requirement(identity.id());
    }
}
