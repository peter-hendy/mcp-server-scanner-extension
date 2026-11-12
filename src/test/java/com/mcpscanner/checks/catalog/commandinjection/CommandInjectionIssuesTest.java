package com.mcpscanner.checks.catalog.commandinjection;

import burp.api.montoya.collaborator.InteractionType;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.issue.Cwe;
import com.mcpscanner.checks.issue.IssueIdentity;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommandInjectionIssuesTest {

    private final CommandInjectionIssues issues = new CommandInjectionIssues("Background prose.", List.of(new Cwe(78, "OS")),
            List.of("https://example.test/ref"));

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void rendersTheKindsNameSeverityConfidenceAndSubject() {
        AuditIssue issue = issues.render(finding(FindingKind.EVALUATED_ARGUMENT, "params.arguments.code"));

        assertThat(issue.name()).isEqualTo("MCP code execution argument");
        assertThat(issue.severity()).isEqualTo(AuditIssueSeverity.INFORMATION);
        assertThat(issue.confidence()).isEqualTo(AuditIssueConfidence.TENTATIVE);
        assertThat(issue.detail()).contains("run_python").contains("params.arguments.code").contains("Python statement");
    }

    @Test
    void listsEachCollaboratorInteractionInTheDetail() {
        Finding finding = new Finding(FindingKind.SHELL_OUT_OF_BAND, subject("f"), "Collaborator DNS interaction",
                List.of(), List.of(com.mcpscanner.checks.shared.collaborator.FakeCollaborator.interaction("a",
                        InteractionType.DNS, null)));

        assertThat(issues.render(finding).detail())
                .contains("Collaborator interactions: DNS interaction from 127.0.0.1 at 2026-11-06T12:00Z.");
    }

    @Test
    void omitsTheInteractionParagraphWhenThereAreNone() {
        assertThat(issues.render(finding(FindingKind.EVALUATED_ARGUMENT, "f")).detail())
                .doesNotContain("Collaborator interactions");
    }

    @Test
    void theSubjectIgnoresTheTechniqueSoRepeatsConsolidate() {
        AuditIssue dns = issues.render(new Finding(FindingKind.SHELL_OUT_OF_BAND, subject("f"), "DNS", List.of()));
        AuditIssue http = issues.render(new Finding(FindingKind.SHELL_OUT_OF_BAND, subject("f"), "HTTP", List.of()));
        AuditIssue other = issues.render(new Finding(FindingKind.SHELL_OUT_OF_BAND, subject("g"), "DNS", List.of()));

        assertThat(IssueIdentity.sameSubject(dns, http)).isTrue();
        assertThat(IssueIdentity.sameSubject(dns, other)).isFalse();
    }

    @Test
    void differentNamesNeverShareASubject() {
        AuditIssue shell = issues.render(finding(FindingKind.SHELL_COMPUTED_RESULT, "f"));
        AuditIssue code = issues.render(finding(FindingKind.INTERPRETER_COMPUTED_RESULT, "f"));

        assertThat(IssueIdentity.sameSubject(shell, code)).isFalse();
    }

    private static Finding finding(FindingKind kind, String field) {
        return new Finding(kind, subject(field), "Python statement", List.of());
    }

    private static FieldSubject subject(String field) {
        return new FieldSubject("http://mcp.example/mcp", "tools/call", "run_python", field);
    }
}
