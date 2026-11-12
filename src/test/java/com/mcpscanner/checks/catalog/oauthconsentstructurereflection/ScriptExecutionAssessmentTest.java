package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptExecutionAssessmentTest {

    @Test
    void noEnforcedPolicyMeansNoPolicy() {
        ScriptExecutionAssessment assessment = ScriptExecutionAssessment.of(List.of());

        assertThat(assessment.verdict()).isEqualTo(ScriptExecution.NO_POLICY);
        assertThat(assessment.summary()).contains("No enforced Content-Security-Policy");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "script-src 'self'",
            "default-src 'self'",
            "default-src 'none'",
            "SCRIPT-SRC 'SELF'",
            "script-src 'nonce-r4nd' 'unsafe-inline'; base-uri 'none'",
            "script-src 'sha256-abc=' 'unsafe-inline'; base-uri 'self'",
            "script-src 'strict-dynamic' 'nonce-r4nd' https: 'unsafe-inline'; base-uri 'none'",
            "default-src *; script-src 'self'"
    })
    void policyThatBlocksInlineAndArbitraryHostsBlocksExecution(String policy) {
        ScriptExecutionAssessment assessment = ScriptExecutionAssessment.of(List.of(policy));

        assertThat(assessment.verdict()).isEqualTo(ScriptExecution.BLOCKED);
        assertThat(assessment.summary()).contains(policy).contains("Same-origin script gadgets were not assessed");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "script-src 'self' 'unsafe-inline'",
            "script-src *",
            "script-src https:",
            "script-src 'self' data:",
            "default-src 'self' 'unsafe-inline'",
            "img-src 'none'",
            "script-src 'self'; script-src-attr 'unsafe-inline'",
            "script-src 'self'; script-src-elem 'self' 'unsafe-inline'",
            "script-src 'self'; script-src-elem http:",
            "script-src https://*",
            "script-src 'self' *.com"
    })
    void policyThatLeavesAnInjectionPathOpenDoesNotBlockExecution(String policy) {
        ScriptExecutionAssessment assessment = ScriptExecutionAssessment.of(List.of(policy));

        assertThat(assessment.verdict()).isEqualTo(ScriptExecution.NOT_BLOCKED);
        assertThat(assessment.summary()).contains(policy);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "script-src 'self' https://cdn.example.com",
            "script-src 'self' cdn.jsdelivr.net",
            "script-src 'nonce-r4nd'",
            "script-src 'sha256-abc='; base-uri https://cdn.example.com",
            "script-src 'nonce-r4nd'; base-uri *"
    })
    void policyThatAllowsListedHostsOrAnUnrestrictedBaseIsUndetermined(String policy) {
        ScriptExecutionAssessment assessment = ScriptExecutionAssessment.of(List.of(policy));

        assertThat(assessment.verdict()).isEqualTo(ScriptExecution.UNDETERMINED);
        assertThat(assessment.summary()).contains(policy).contains("was not assessed").doesNotContain("allows no");
    }

    @Test
    void strictestPolicyAmongSeveralDecides() {
        assertThat(ScriptExecutionAssessment.of(List.of("script-src 'unsafe-inline'", "script-src 'self' cdn.example"))
                .verdict()).isEqualTo(ScriptExecution.UNDETERMINED);
    }

    @Test
    void anyOneBlockingPolicyAmongSeveralBlocksExecution() {
        assertThat(ScriptExecutionAssessment.of(List.of("script-src 'unsafe-inline'", "script-src 'self'"))
                .verdict()).isEqualTo(ScriptExecution.BLOCKED);
    }

    @Test
    void commaSeparatedPoliciesInOneHeaderAreEachEnforced() {
        assertThat(ScriptExecutionAssessment.of(List.of("script-src 'unsafe-inline', script-src 'self'"))
                .verdict()).isEqualTo(ScriptExecution.BLOCKED);
    }

    @Test
    void blankPoliciesAreIgnored() {
        assertThat(ScriptExecutionAssessment.of(List.of("  ", "")).verdict()).isEqualTo(ScriptExecution.NO_POLICY);
    }

    @Test
    void firstDuplicateDirectiveWins() {
        assertThat(ScriptExecutionAssessment.of(List.of("script-src 'self'; script-src 'unsafe-inline'"))
                .verdict()).isEqualTo(ScriptExecution.BLOCKED);
    }
}
