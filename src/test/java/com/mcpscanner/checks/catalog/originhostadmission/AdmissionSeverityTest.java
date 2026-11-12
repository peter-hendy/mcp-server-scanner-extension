package com.mcpscanner.checks.catalog.originhostadmission;

import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AdmissionSeverityTest {

    @ParameterizedTest
    @CsvSource({
            "HOST_AND_ORIGIN, true, false, false, MEDIUM",
            "HOST_AND_ORIGIN, true, true, false, LOW",
            "HOST_AND_ORIGIN, true, false, true, INFORMATION",
            "HOST_AND_ORIGIN, true, true, true, INFORMATION",
            "HOST_AND_ORIGIN, false, false, false, INFORMATION",
            "HOST_AND_ORIGIN, false, true, false, INFORMATION",
            "ORIGIN, true, false, false, INFORMATION",
            "ORIGIN, true, true, false, INFORMATION",
            "ORIGIN, false, false, true, INFORMATION",
            "HOST, true, false, false, INFORMATION",
            "HOST, false, true, true, INFORMATION"})
    void raisesOnlyTheReboundPairOnALocalPlaintextTarget(AdmissionCategory category, boolean locallyReachable,
                                                        boolean credentialed, boolean tls,
                                                        AuditIssueSeverity expected) {
        assertThat(AdmissionSeverity.of(category, new TargetExposure(locallyReachable, credentialed, tls)))
                .isEqualTo(expected);
    }
}
