package com.mcpscanner.checks.api.crossidentity;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.ConsolidationAction;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.mcpscanner.checks.api.crossidentity.FakeMcpTarget.resourceRead;
import static com.mcpscanner.checks.api.crossidentity.FakeMcpTarget.toolCall;
import static org.assertj.core.api.Assertions.assertThat;

class IdentityIssueTest {

    private static final IdentityIssue ISSUE = new IdentityIssue("MCP operation available", AuditIssueSeverity.HIGH,
            AuditIssueConfidence.TENTATIVE, "background", "strength",
            new IdentityObservation(TestPrincipals.peer("B"), "vertical", "observed", List.of()));

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void readReplayStatesTheProbeContractWithoutAStateChange() {
        assertThat(rendered(resourceRead("notes://alice/private.md", "A"), ReplayedOperation.READ).detail())
                .contains("replayed as supplied for every arm.").doesNotContain("state-changing");
    }

    @Test
    void writeReplayStatesThatAStateChangingOperationWasRepeated() {
        assertThat(rendered(toolCall("delete_note", "{}", "A"), ReplayedOperation.WRITE).detail())
                .contains("so a state-changing operation was repeated.");
    }

    @Test
    void theSameItemAndIdentityConsolidatesIntoTheIssueAlreadyFiled() {
        AuditIssue filed = rendered(resourceRead("notes://alice/private.md", "A"), ReplayedOperation.READ);
        AuditIssue again = rendered(resourceRead("notes://alice/private.md", "A"), ReplayedOperation.READ);

        assertThat(IdentityIssue.consolidate(filed, again)).isEqualTo(ConsolidationAction.KEEP_EXISTING);
    }

    @Test
    void anotherItemAtTheSameUrlAndNameIsKeptBesideIt() {
        AuditIssue filed = rendered(resourceRead("notes://alice/private.md", "A"), ReplayedOperation.READ);
        AuditIssue other = rendered(resourceRead("notes://bob/private.md", "A"), ReplayedOperation.READ);

        assertThat(IdentityIssue.consolidate(filed, other)).isEqualTo(ConsolidationAction.KEEP_BOTH);
    }

    private static AuditIssue rendered(HttpRequest request, ReplayedOperation replayed) {
        return ISSUE.render(OperationSeed.of(request).orElseThrow(), replayed, List.of(), List.of());
    }
}
