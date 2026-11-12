package com.mcpscanner.checks.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrincipalViewTest {

    @Test
    void lowerRanksBelowPrimaryAndPeerWhichRankBelowHigher() {
        assertThat(PrincipalRole.LOWER.rank()).isLessThan(PrincipalRole.PRIMARY.rank());
        assertThat(PrincipalRole.PRIMARY.rank()).isEqualTo(PrincipalRole.PEER.rank());
        assertThat(PrincipalRole.PEER.rank()).isLessThan(PrincipalRole.HIGHER.rank());
    }

    @Test
    void emptyRosterHasAnUncredentialedPrimaryAndNoComparedPrincipals() {
        PrincipalRoster roster = PrincipalRoster.empty();

        assertThat(roster.primary().role()).isEqualTo(PrincipalRole.PRIMARY);
        assertThat(roster.primary().credentialHeaders().get()).isEmpty();
        assertThat(roster.primary().privateMarker()).isEmpty();
        assertThat(roster.compared()).isEmpty();
    }

    @Test
    void rosterOfKeepsTheGivenPrincipalsImmutably() {
        Principal bob = new Principal("b", "bob", PrincipalRole.PEER, () -> Map.of("Authorization", "Bearer b"),
                Optional.of("marker"), Map.of("doc_id", "1"));
        Principal primary = Principal.primary(() -> Map.of("Authorization", "Bearer a"));

        PrincipalRoster roster = PrincipalRoster.of(primary, List.of(bob));

        assertThat(roster.primary()).isSameAs(primary);
        assertThat(roster.compared()).containsExactly(bob);
        assertThatThrownBy(() -> roster.compared().add(bob)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void credentialHeadersAreReadLiveFromTheSupplier() {
        String[] token = {"first"};
        Principal primary = Principal.primary(() -> Map.of("Authorization", token[0]));

        token[0] = "second";

        assertThat(primary.credentialHeaders().get()).containsEntry("Authorization", "second");
    }

    @Test
    void principalRejectsMissingParts() {
        assertThatThrownBy(() -> new Principal(null, "x", PrincipalRole.PEER, Map::of, Optional.empty(), Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Principal("x", "x", PrincipalRole.PEER, null, Optional.empty(), Map.of()))
                .isInstanceOf(NullPointerException.class);
    }
}
