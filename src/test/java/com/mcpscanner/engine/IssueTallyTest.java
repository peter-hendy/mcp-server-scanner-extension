package com.mcpscanner.engine;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IssueTallyTest {

    @Test
    void keepsTheLatestIssuesUpToTheBoundWhileCountingEveryIssueBySeverity() {
        IssueTally tally = new IssueTally();
        List<AuditIssue> highs = IntStream.range(0, IssueTally.KEPT).mapToObj(i -> rated(AuditIssueSeverity.HIGH))
                .toList();
        AuditIssue info = rated(AuditIssueSeverity.INFORMATION);

        tally.add(highs, Optional.empty());
        tally.add(List.of(info), Optional.empty());

        ScanFindings findings = tally.snapshot();
        assertThat(findings.total()).isEqualTo(IssueTally.KEPT + 1);
        assertThat(findings.kept()).hasSize(IssueTally.KEPT).endsWith(info).doesNotContain(highs.get(0));
        assertThat(findings.count(AuditIssueSeverity.HIGH)).isEqualTo(IssueTally.KEPT);
        assertThat(findings.count(AuditIssueSeverity.INFORMATION)).isEqualTo(1);
        assertThat(findings.count(AuditIssueSeverity.LOW)).isZero();
    }

    @Test
    void remembersTheTestedRequestOfEachKeptIssueUntilItIsEvicted() {
        IssueTally tally = new IssueTally();
        AuditIssue first = rated(AuditIssueSeverity.HIGH);
        AuditIssue untested = rated(AuditIssueSeverity.LOW);
        List<AuditIssue> later = IntStream.range(0, IssueTally.KEPT - 2).mapToObj(i -> rated(AuditIssueSeverity.LOW))
                .toList();
        AuditIssue last = rated(AuditIssueSeverity.HIGH);

        tally.add(List.of(first), Optional.of("tools/call ping_host"));
        tally.add(List.of(untested), Optional.empty());
        tally.add(later, Optional.of("tools/call other"));
        ScanFindings beforeEviction = tally.snapshot();
        tally.add(List.of(last), Optional.of("prompts/get summarize"));
        ScanFindings afterEviction = tally.snapshot();

        assertThat(beforeEviction.testedRequest(first)).contains("tools/call ping_host");
        assertThat(beforeEviction.testedRequest(untested)).isEmpty();
        assertThat(afterEviction.testedRequest(first)).isEmpty();
        assertThat(afterEviction.testedRequest(last)).contains("prompts/get summarize");
        assertThat(afterEviction.testedRequest(later.get(0))).contains("tools/call other");
    }

    private static AuditIssue rated(AuditIssueSeverity severity) {
        AuditIssue issue = mock(AuditIssue.class);
        when(issue.severity()).thenReturn(severity);
        return issue;
    }
}
