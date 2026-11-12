package com.mcpscanner.identity;

import com.mcpscanner.config.AuthTypeNames;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthGrantMigrationTest {

    private final Identity userOne = Identity.create("User 1", IdentityRole.PEER, IdentityAuthKind.DETECT);
    private final Identity bob = Identity.create("bob", IdentityRole.PEER, IdentityAuthKind.BEARER);

    @Test
    void theSavedGrantBecomesThePrimaryUsersKind() {
        assertThat(kinds(OAuthGrantMigration.apply(stored(), AuthTypeNames.OAUTH, "CLIENT_CREDENTIALS")))
                .containsExactly(IdentityAuthKind.OAUTH_CLIENT_CREDENTIALS, IdentityAuthKind.BEARER);
        assertThat(kinds(OAuthGrantMigration.apply(stored(), null, "ENTERPRISE_MANAGED")))
                .containsExactly(IdentityAuthKind.OAUTH_ENTERPRISE_MANAGED, IdentityAuthKind.BEARER);
    }

    @Test
    void theOldestGrantAuthTypesMigrateToo() {
        assertThat(kinds(OAuthGrantMigration.apply(stored(), "Client Credentials", null)))
                .first().isEqualTo(IdentityAuthKind.OAUTH_CLIENT_CREDENTIALS);
        assertThat(kinds(OAuthGrantMigration.apply(stored(), "Enterprise Managed", null)))
                .first().isEqualTo(IdentityAuthKind.OAUTH_ENTERPRISE_MANAGED);
    }

    @Test
    void anAuthorizationCodeGrantOrANonOAuthAuthTypeLeavesTheUsersAlone() {
        StoredIdentities stored = stored();

        assertThat(OAuthGrantMigration.apply(stored, AuthTypeNames.OAUTH, "AUTHORIZATION_CODE")).isSameAs(stored);
        assertThat(OAuthGrantMigration.apply(stored, AuthTypeNames.BEARER, "CLIENT_CREDENTIALS")).isSameAs(stored);
        assertThat(OAuthGrantMigration.apply(stored, null, null)).isSameAs(stored);
    }

    private StoredIdentities stored() {
        return new StoredIdentities(List.of(userOne, bob), Optional.of(userOne.id()));
    }

    private static List<IdentityAuthKind> kinds(StoredIdentities stored) {
        return stored.identities().stream().map(Identity::authKind).toList();
    }
}
