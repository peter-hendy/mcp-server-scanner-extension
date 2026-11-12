package com.mcpscanner.checks.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialCopiesTest {

    private static final Principal BOB = principal("bob", "Bearer bob");
    private static final Principal BOB_AGAIN = principal("bob2", "Bearer bob");
    private static final Principal ALICE = principal("alice", "Bearer alice");
    private static final Principal ALICE_AGAIN = principal("alice2", "Bearer alice");
    private static final Principal CAROL = principal("carol", "Bearer carol");

    @Test
    void ownerIsNeverACopy() {
        CredentialCopies copies = new CredentialCopies(BOB, List.of(BOB, BOB_AGAIN));

        assertThat(copies.originalOf(BOB)).isEmpty();
    }

    @Test
    void principalSharingTheOwnersCredentialsIsACopyOfTheOwner() {
        CredentialCopies copies = new CredentialCopies(BOB, List.of(BOB_AGAIN, BOB));

        assertThat(copies.originalOf(BOB_AGAIN)).contains(BOB);
    }

    @Test
    void laterTwinIsACopyOfTheEarlierOne() {
        CredentialCopies copies = new CredentialCopies(BOB, List.of(BOB, ALICE, CAROL, ALICE_AGAIN));

        assertThat(copies.originalOf(ALICE)).isEmpty();
        assertThat(copies.originalOf(ALICE_AGAIN)).contains(ALICE);
        assertThat(copies.originalOf(CAROL)).isEmpty();
    }

    @Test
    void principalsWithoutCredentialsAreNeverCopies() {
        Principal first = principal("first", null);
        Principal second = principal("second", null);
        CredentialCopies copies = new CredentialCopies(BOB, List.of(first, second));

        assertThat(copies.originalOf(second)).isEmpty();
    }

    private static Principal principal(String id, String token) {
        Map<String, String> headers = token == null ? Map.of() : Map.of("Authorization", token);
        return new Principal(id, id, PrincipalRole.PEER, () -> headers, Optional.empty(), Map.of());
    }
}
