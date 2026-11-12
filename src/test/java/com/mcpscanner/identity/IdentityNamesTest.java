package com.mcpscanner.identity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityNamesTest {

    private static final IdentityNames NAMES = IdentityNames.of(List.of(named(" Alice "), named("user"),
            named("User 2"), named("User 1")));

    @Test
    void aNameIsTakenIgnoringCaseAndSurroundingSpace() {
        assertThat(NAMES.isTaken("ALICE")).isTrue();
        assertThat(NAMES.isTaken(" alice  ")).isTrue();
        assertThat(NAMES.isTaken("bob")).isFalse();
    }

    @Test
    void anUnusedNameKeepsAFreeBaseOrNumbersItFromTwo() {
        assertThat(NAMES.unused("bob")).isEqualTo("bob");
        assertThat(NAMES.unused("User")).isEqualTo("User 3");
    }

    @Test
    void anUnusedNumberedNameStartsAtOne() {
        assertThat(NAMES.unusedNumbered("Peer")).isEqualTo("Peer 1");
        assertThat(NAMES.unusedNumbered("User")).isEqualTo("User 3");
    }

    private static Identity named(String name) {
        return Identity.create(name, IdentityRole.PEER, IdentityAuthKind.NONE);
    }
}
