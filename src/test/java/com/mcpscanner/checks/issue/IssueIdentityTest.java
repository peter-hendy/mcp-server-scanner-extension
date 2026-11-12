package com.mcpscanner.checks.issue;

import burp.api.montoya.scanner.ConsolidationAction;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IssueIdentityTest {

    private static final String SUBJECT = "<p>Field: a</p>";

    @Test
    void issuesWithTheSameNameAndFirstParagraphHaveTheSameSubject() {
        assertThat(IssueIdentity.sameSubject(issue("Name", SUBJECT + "<p>one</p>"),
                issue("Name", SUBJECT + "<p>two</p>"))).isTrue();
    }

    @Test
    void aDifferentFirstParagraphOrNameIsADifferentSubject() {
        assertThat(IssueIdentity.sameSubject(issue("Name", SUBJECT), issue("Name", "<p>Field: b</p>"))).isFalse();
        assertThat(IssueIdentity.sameSubject(issue("Name", SUBJECT), issue("Other", SUBJECT))).isFalse();
    }

    @Test
    void aDetailWithoutParagraphsIsComparedWhole() {
        assertThat(IssueIdentity.sameSubject(issue("Name", "plain"), issue("Name", "plain"))).isTrue();
        assertThat(IssueIdentity.sameSubject(issue("Name", "plain"), issue("Name", "other"))).isFalse();
    }

    @Test
    void aMissingDetailNeverMatches() {
        assertThat(IssueIdentity.sameSubject(issue("Name", null), issue("Name", SUBJECT))).isFalse();
        assertThat(IssueIdentity.sameSubject(issue("Name", SUBJECT), issue("Name", null))).isFalse();
        assertThat(IssueIdentity.sameDetail(issue("Name", null), issue("Name", null))).isFalse();
    }

    @Test
    void sameDetailComparesTheWholeDetail() {
        assertThat(IssueIdentity.sameDetail(issue("Name", SUBJECT + "<p>one</p>"),
                issue("Name", SUBJECT + "<p>one</p>"))).isTrue();
        assertThat(IssueIdentity.sameDetail(issue("Name", SUBJECT + "<p>one</p>"),
                issue("Name", SUBJECT + "<p>two</p>"))).isFalse();
        assertThat(IssueIdentity.sameDetail(issue("Name", SUBJECT), issue("Other", SUBJECT))).isFalse();
    }

    @Test
    void theSameIssueKeepsTheExistingOneAndADifferentIssueKeepsBoth() {
        assertThat(IssueIdentity.keepExistingWhen(true)).isEqualTo(ConsolidationAction.KEEP_EXISTING);
        assertThat(IssueIdentity.keepExistingWhen(false)).isEqualTo(ConsolidationAction.KEEP_BOTH);
    }

    @Test
    void theStrongerIssueOfTheSameSubjectIsKept() {
        AuditIssue tentative = rated(AuditIssueSeverity.MEDIUM, AuditIssueConfidence.TENTATIVE);
        AuditIssue firm = rated(AuditIssueSeverity.HIGH, AuditIssueConfidence.FIRM);

        assertThat(IssueIdentity.keepStrongerOfSameSubject(tentative, firm)).isEqualTo(ConsolidationAction.KEEP_NEW);
        assertThat(IssueIdentity.keepStrongerOfSameSubject(firm, tentative))
                .isEqualTo(ConsolidationAction.KEEP_EXISTING);
        assertThat(IssueIdentity.keepStrongerOfSameSubject(firm, firm)).isEqualTo(ConsolidationAction.KEEP_EXISTING);
    }

    @Test
    void differentSubjectsAreBothKeptWhateverTheirRating() {
        AuditIssue other = issue("Name", "<p>Field: b</p>");
        when(other.severity()).thenReturn(AuditIssueSeverity.HIGH);

        assertThat(IssueIdentity.keepStrongerOfSameSubject(
                rated(AuditIssueSeverity.MEDIUM, AuditIssueConfidence.TENTATIVE), other))
                .isEqualTo(ConsolidationAction.KEEP_BOTH);
    }

    private static AuditIssue rated(AuditIssueSeverity severity, AuditIssueConfidence confidence) {
        AuditIssue issue = issue("Name", SUBJECT);
        when(issue.severity()).thenReturn(severity);
        when(issue.confidence()).thenReturn(confidence);
        return issue;
    }

    private static AuditIssue issue(String name, String detail) {
        AuditIssue issue = mock(AuditIssue.class);
        when(issue.name()).thenReturn(name);
        when(issue.detail()).thenReturn(detail);
        return issue;
    }
}
