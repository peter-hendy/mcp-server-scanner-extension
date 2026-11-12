package com.mcpscanner.checks.shared.probe;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SendBudgetTest {

    @Test
    void affordsSendsUpToItsLimitThenIsExhausted() {
        SendBudget budget = new SendBudget(3);
        budget.record(false);

        assertThat(budget.affords(2)).isTrue();
        assertThat(budget.affords(3)).isFalse();
        budget.record(false);
        budget.record(false);
        assertThat(budget.exhausted()).isTrue();
        assertThat(budget.sent()).isEqualTo(3);
    }

    @Test
    void aRateLimitedReplyExhaustsItAtOnce() {
        SendBudget budget = new SendBudget(10);
        budget.record(true);

        assertThat(budget.rateLimited()).isTrue();
        assertThat(budget.exhausted()).isTrue();
        assertThat(budget.affords(1)).isFalse();
    }
}
