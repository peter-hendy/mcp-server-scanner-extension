package com.mcpscanner.identity;

import com.mcpscanner.config.AuthTypeNames;

import java.util.Optional;

final class OAuthGrantMigration {

    private static final String CLIENT_CREDENTIALS = "CLIENT_CREDENTIALS";
    private static final String ENTERPRISE_MANAGED = "ENTERPRISE_MANAGED";
    private static final String LEGACY_CLIENT_CREDENTIALS = "Client Credentials";
    private static final String LEGACY_ENTERPRISE_MANAGED = "Enterprise Managed";

    private OAuthGrantMigration() {
    }

    static StoredIdentities apply(StoredIdentities stored, String savedAuthType, String savedGrant) {
        return savedGrantKind(savedAuthType, savedGrant)
                .map(kind -> withPrimaryKind(stored, kind))
                .orElse(stored);
    }

    private static Optional<IdentityAuthKind> savedGrantKind(String savedAuthType, String savedGrant) {
        if (LEGACY_CLIENT_CREDENTIALS.equals(savedAuthType)) {
            return Optional.of(IdentityAuthKind.OAUTH_CLIENT_CREDENTIALS);
        }
        if (LEGACY_ENTERPRISE_MANAGED.equals(savedAuthType)) {
            return Optional.of(IdentityAuthKind.OAUTH_ENTERPRISE_MANAGED);
        }
        if (savedAuthType != null && !AuthTypeNames.OAUTH.equals(savedAuthType)) {
            return Optional.empty();
        }
        if (CLIENT_CREDENTIALS.equals(savedGrant)) {
            return Optional.of(IdentityAuthKind.OAUTH_CLIENT_CREDENTIALS);
        }
        return ENTERPRISE_MANAGED.equals(savedGrant) ? Optional.of(IdentityAuthKind.OAUTH_ENTERPRISE_MANAGED)
                : Optional.empty();
    }

    private static StoredIdentities withPrimaryKind(StoredIdentities stored, IdentityAuthKind kind) {
        String primaryId = stored.primaryId().orElseThrow();
        return new StoredIdentities(stored.identities().stream()
                .map(identity -> identity.id().equals(primaryId) ? identity.withAuthKind(kind) : identity)
                .toList(), stored.primaryId());
    }
}
