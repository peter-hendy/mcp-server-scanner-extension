package com.mcpscanner.identity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mcpscanner.config.ExtensionConfigStore;
import com.mcpscanner.logging.McpEventLog;

import java.util.List;

public final class IdentityStore {

    private final ExtensionConfigStore config;
    private final PersistedJsonDocument<List<PersistedIdentity>> identities;
    private final PersistedJsonReader<LegacyPrimaryProfile> legacyPrimaryProfile;

    public IdentityStore(ExtensionConfigStore config, McpEventLog eventLog) {
        this.config = config;
        this.identities = new PersistedJsonDocument<>("identities", new TypeReference<>() {},
                config::identitiesJson, config::setIdentitiesJson, config::setUnreadableIdentitiesBackupJson,
                eventLog);
        this.legacyPrimaryProfile = new PersistedJsonReader<>("primary identity profile", new TypeReference<>() {},
                config::primaryProfileJson, eventLog);
    }

    public StoredIdentities load() {
        List<PersistedIdentity> persisted = identities.read().orElse(List.of());
        IdentityAuthKind legacyKind = legacyAuthKind();
        return new StoredIdentities(persisted.stream().map(identity -> identity.toIdentity(legacyKind)).toList(),
                persisted.stream().filter(PersistedIdentity::connectAs).map(PersistedIdentity::id).findFirst());
    }

    public void save(StoredIdentities stored) {
        identities.write(stored.identities().stream()
                .map(identity -> PersistedIdentity.from(identity, isPrimary(stored, identity)))
                .toList());
    }

    private static boolean isPrimary(StoredIdentities stored, Identity identity) {
        return stored.primaryId().filter(identity.id()::equals).isPresent();
    }

    public LegacyPrimary legacyPrimary() {
        return legacyPrimaryProfile.read().orElse(LegacyPrimaryProfile.EMPTY).withAuthKind(legacyAuthKind());
    }

    boolean detectMigrationPending() {
        return !config.autoDetectMigrated();
    }

    boolean legacyAutoDetect() {
        return config.legacyAutoDetectAuth();
    }

    void markDetectMigrated() {
        config.markAutoDetectMigrated();
    }

    StoredIdentities migrateOAuthGrant(StoredIdentities stored) {
        return config.oauthGrantMigrated() ? stored
                : OAuthGrantMigration.apply(stored, config.authType(), config.oauthGrant());
    }

    void retireOAuthGrant() {
        if (!config.oauthGrantMigrated()) {
            config.retireOauthGrant();
        }
    }

    public void clearLegacyPrimary() {
        config.clearPrimaryProfileJson();
    }

    private IdentityAuthKind legacyAuthKind() {
        return LegacyAuthenticationKind.of(config.authType());
    }
}
