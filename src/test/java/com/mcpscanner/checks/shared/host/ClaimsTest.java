package com.mcpscanner.checks.shared.host;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimsTest {

    private final Claims<String> claims = new Claims<>();

    @Test
    void eachKeyIsClaimedIndependently() {
        assertThat(claims.tryClaim("host as b")).isTrue();
        assertThat(claims.tryClaim("host as c")).isTrue();
        assertThat(claims.tryClaim("host as b")).isFalse();
    }

    @Test
    void probedAttemptsAreCountedPerKey() {
        for (int attempt = 0; attempt < Claims.MAX_PROBED_ATTEMPTS; attempt++) {
            claims.tryClaim("host as b");
            claims.releaseProbed("host as b");
        }

        assertThat(claims.tryClaim("host as b")).isFalse();
        assertThat(claims.tryClaim("host as c")).isTrue();
    }

    @Test
    void clearingReleasesEveryKeyAndResetsItsBudgets() {
        for (int seed = 0; seed < Claims.MAX_UNSUITABLE_SEEDS; seed++) {
            claims.tryClaim("host as b");
            claims.releaseUnsuitableSeed("host as b");
        }
        claims.clear();

        assertThat(claims.tryClaim("host as b")).isTrue();
    }

    @Test
    void saysAKeyIsRefusedWhileItIsHeld() {
        claims.tryClaim("host as b");

        assertThat(claims.whyRefused("host as b")).isEqualTo(Claims.Refusal.HELD);
    }

    @Test
    void saysAKeyIsRefusedOnceItsProbedAttemptsAreSpent() {
        for (int attempt = 0; attempt < Claims.MAX_PROBED_ATTEMPTS; attempt++) {
            claims.tryClaim("host as b");
            claims.releaseProbed("host as b");
        }

        assertThat(claims.whyRefused("host as b")).isEqualTo(Claims.Refusal.PROBED_ATTEMPTS_SPENT);
    }

    @Test
    void saysAKeyIsRefusedOnceItsUnsuitableSeedsAreSpent() {
        for (int seed = 0; seed < Claims.MAX_UNSUITABLE_SEEDS; seed++) {
            claims.tryClaim("host as b");
            claims.releaseUnsuitableSeed("host as b");
        }

        assertThat(claims.whyRefused("host as b")).isEqualTo(Claims.Refusal.UNSUITABLE_SEEDS_SPENT);
    }
}
