package com.mcpscanner.checks.api;

import com.mcpscanner.checks.shared.host.Claims;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HostRefusalsTest {

    @Test
    void aHeldHostWasAlreadyAudited() {
        assertThat(HostRefusals.reasonFor(Claims.Refusal.HELD).kind()).isEqualTo(SkipReason.Kind.ALREADY_AUDITED);
    }

    @Test
    void spentAttemptsUseUpTheHostBudget() {
        assertThat(HostRefusals.reasonFor(Claims.Refusal.PROBED_ATTEMPTS_SPENT).kind())
                .isEqualTo(SkipReason.Kind.BUDGET_EXHAUSTED);
        assertThat(HostRefusals.reasonFor(Claims.Refusal.UNSUITABLE_SEEDS_SPENT).kind())
                .isEqualTo(SkipReason.Kind.BUDGET_EXHAUSTED);
    }
}
