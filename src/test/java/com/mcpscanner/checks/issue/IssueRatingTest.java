package com.mcpscanner.checks.issue;

import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IssueRatingTest {

    @Test
    void ratingsWithTheSameSeverityAndConfidenceAreEqual() {
        IssueRating rating = new IssueRating(AuditIssueSeverity.HIGH, AuditIssueConfidence.FIRM);

        assertThat(rating).isEqualTo(new IssueRating(AuditIssueSeverity.HIGH, AuditIssueConfidence.FIRM));
        assertThat(rating.severity()).isEqualTo(AuditIssueSeverity.HIGH);
        assertThat(rating.confidence()).isEqualTo(AuditIssueConfidence.FIRM);
    }

    @Test
    void higherSeverityIsStrongerWhateverTheConfidence() {
        assertThat(new IssueRating(AuditIssueSeverity.HIGH, AuditIssueConfidence.TENTATIVE)
                .isStrongerThan(new IssueRating(AuditIssueSeverity.MEDIUM, AuditIssueConfidence.CERTAIN))).isTrue();
        assertThat(new IssueRating(AuditIssueSeverity.MEDIUM, AuditIssueConfidence.CERTAIN)
                .isStrongerThan(new IssueRating(AuditIssueSeverity.HIGH, AuditIssueConfidence.TENTATIVE))).isFalse();
    }

    @Test
    void equalSeverityIsDecidedByConfidence() {
        assertThat(new IssueRating(AuditIssueSeverity.MEDIUM, AuditIssueConfidence.FIRM)
                .isStrongerThan(new IssueRating(AuditIssueSeverity.MEDIUM, AuditIssueConfidence.TENTATIVE))).isTrue();
        assertThat(new IssueRating(AuditIssueSeverity.MEDIUM, AuditIssueConfidence.FIRM)
                .isStrongerThan(new IssueRating(AuditIssueSeverity.MEDIUM, AuditIssueConfidence.FIRM))).isFalse();
    }

    @Test
    void ratingIsReadFromAnIssue() {
        AuditIssue issue = mock(AuditIssue.class);
        when(issue.severity()).thenReturn(AuditIssueSeverity.LOW);
        when(issue.confidence()).thenReturn(AuditIssueConfidence.CERTAIN);

        assertThat(IssueRating.of(issue)).isEqualTo(new IssueRating(AuditIssueSeverity.LOW, AuditIssueConfidence.CERTAIN));
    }
}
