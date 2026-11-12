package com.mcpscanner.checks.catalog.oauthrefreshgrant;

import com.mcpscanner.checks.shared.auth.oracle.OracleVerdict;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UsabilityTest {

    @Test
    void onlyAHitProvesATokenUsable() {
        assertThat(Usability.of(OracleVerdict.HIT)).isEqualTo(Usability.USABLE);
    }

    @Test
    void aRefusedOrDifferentCandidateIsNotUsable() {
        assertThat(Usability.of(OracleVerdict.CANDIDATE_NOT_SUCCESSFUL)).isEqualTo(Usability.NOT_USABLE);
        assertThat(Usability.of(OracleVerdict.CANDIDATE_NOT_EQUIVALENT)).isEqualTo(Usability.NOT_USABLE);
    }

    @Test
    void aBaselineOrControlProblemLeavesUsabilityUnverified() {
        assertThat(Usability.of(OracleVerdict.BASELINE_NOT_SUCCESSFUL)).isEqualTo(Usability.UNVERIFIED);
        assertThat(Usability.of(OracleVerdict.NO_REFUSAL_CONTROLS)).isEqualTo(Usability.UNVERIFIED);
        assertThat(Usability.of(OracleVerdict.CONTROL_NOT_REFUSED)).isEqualTo(Usability.UNVERIFIED);
    }
}
