package com.mcpscanner.ui.widgets;

import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SeverityOrderTest {

    @Test
    void ordersSeveritiesMostSevereFirstAndUnratedLast() {
        List<AuditIssueSeverity> shuffled = Arrays.asList(AuditIssueSeverity.LOW, null, AuditIssueSeverity.HIGH,
                AuditIssueSeverity.FALSE_POSITIVE, AuditIssueSeverity.INFORMATION, AuditIssueSeverity.MEDIUM);

        assertThat(shuffled.stream().sorted(SeverityOrder.MOST_SEVERE_FIRST).toList()).containsExactly(
                AuditIssueSeverity.HIGH, AuditIssueSeverity.MEDIUM, AuditIssueSeverity.LOW,
                AuditIssueSeverity.INFORMATION, AuditIssueSeverity.FALSE_POSITIVE, null);
    }

    @Test
    void ordersConfidenceMostConfidentFirst() {
        List<AuditIssueConfidence> shuffled = Arrays.asList(AuditIssueConfidence.TENTATIVE, null,
                AuditIssueConfidence.CERTAIN, AuditIssueConfidence.FIRM);

        assertThat(shuffled.stream().sorted(SeverityOrder.MOST_CONFIDENT_FIRST).toList()).containsExactly(
                AuditIssueConfidence.CERTAIN, AuditIssueConfidence.FIRM, AuditIssueConfidence.TENTATIVE, null);
    }

    @Test
    void listsTheRatedSeveritiesMostSevereFirst() {
        assertThat(SeverityOrder.RATED).containsExactly(AuditIssueSeverity.HIGH, AuditIssueSeverity.MEDIUM,
                AuditIssueSeverity.LOW, AuditIssueSeverity.INFORMATION);
    }
}
