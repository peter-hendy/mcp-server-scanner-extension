package com.mcpscanner.ui.widgets;

import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeverityLabelsTest {

    @Test
    void namesEverySeverityShortly() {
        assertThat(SeverityLabels.of(AuditIssueSeverity.HIGH)).isEqualTo("High");
        assertThat(SeverityLabels.of(AuditIssueSeverity.MEDIUM)).isEqualTo("Medium");
        assertThat(SeverityLabels.of(AuditIssueSeverity.LOW)).isEqualTo("Low");
        assertThat(SeverityLabels.of(AuditIssueSeverity.INFORMATION)).isEqualTo("Info");
        assertThat(SeverityLabels.of(AuditIssueSeverity.FALSE_POSITIVE)).isEqualTo("False positive");
    }

    @Test
    void namesEveryConfidence() {
        assertThat(SeverityLabels.of(AuditIssueConfidence.CERTAIN)).isEqualTo("Certain");
        assertThat(SeverityLabels.of(AuditIssueConfidence.FIRM)).isEqualTo("Firm");
        assertThat(SeverityLabels.of(AuditIssueConfidence.TENTATIVE)).isEqualTo("Tentative");
    }

    @Test
    void anUnratedIssueHasNoLabel() {
        assertThat(SeverityLabels.of((AuditIssueSeverity) null)).isEmpty();
        assertThat(SeverityLabels.of((AuditIssueConfidence) null)).isEmpty();
    }
}
