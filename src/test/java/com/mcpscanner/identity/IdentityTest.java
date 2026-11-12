package com.mcpscanner.identity;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityTest {

    @Test
    void newIdentityHasAFreshIdAndIsComparedWithNothingElseConfigured() {
        Identity bob = Identity.create("bob", IdentityRole.PEER, IdentityAuthKind.BEARER);

        assertThat(bob.id()).isNotBlank();
        assertThat(Identity.create("bob", IdentityRole.PEER, IdentityAuthKind.BEARER).id()).isNotEqualTo(bob.id());
        assertThat(bob.compared()).isTrue();
        assertThat(bob.privateMarker()).isEmpty();
        assertThat(bob.ownedValues().isEmpty()).isTrue();
        assertThat(bob.oauth()).isEqualTo(IdentityOAuthSettings.none());
        assertThat(bob.headerNames()).isEmpty();
    }

    @Test
    void nullOptionalPartsBecomeEmpty() {
        Identity identity = new Identity("id", "bob", IdentityRole.LOWER, IdentityAuthKind.CUSTOM_HEADERS, false,
                null, null, null, null);

        assertThat(identity.privateMarker()).isEqualTo(Optional.empty());
        assertThat(identity.ownedValues()).isEqualTo(OwnedValues.NONE);
        assertThat(identity.oauth()).isEqualTo(IdentityOAuthSettings.none());
        assertThat(identity.headerNames()).isEmpty();
    }

    @Test
    void rejectsMissingRequiredParts() {
        assertThatThrownBy(() -> new Identity(null, "bob", IdentityRole.PEER, IdentityAuthKind.BEARER, true,
                Optional.empty(), OwnedValues.NONE, IdentityOAuthSettings.none(), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Identity("id", "bob", null, IdentityAuthKind.BEARER, true,
                Optional.empty(), OwnedValues.NONE, IdentityOAuthSettings.none(), List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void copyGetsANewIdAndTheGivenNameButKeepsEverythingElse() {
        Identity original = Identity.create("bob", IdentityRole.HIGHER, IdentityAuthKind.OAUTH)
                .withPrivateMarker("bob-secret")
                .withOwnedValues(OwnedValues.entered(Map.of("doc_id", "42")));

        Identity copy = original.copyNamed("bob copy");

        assertThat(copy.id()).isNotEqualTo(original.id());
        assertThat(copy.name()).isEqualTo("bob copy");
        assertThat(copy.role()).isEqualTo(IdentityRole.HIGHER);
        assertThat(copy.privateMarker()).contains("bob-secret");
        assertThat(copy.ownedValues().ownerArguments().all()).containsEntry("doc_id", "42");
    }

    @Test
    void rolesCarryTheirDisplayLabels() {
        assertThat(IdentityRole.PEER.label()).isEqualTo("Same access (peer)");
        assertThat(IdentityRole.LOWER.label()).isEqualTo("Less access");
        assertThat(IdentityRole.HIGHER.label()).isEqualTo("More access");
    }
}
