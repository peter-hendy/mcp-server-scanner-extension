package com.mcpscanner.checks.api;

import burp.api.montoya.collaborator.CollaboratorPayload;
import burp.api.montoya.collaborator.InteractionType;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.checks.shared.collaborator.CollaboratorWatch;
import com.mcpscanner.checks.shared.collaborator.FakeCollaborator;
import com.mcpscanner.checks.shared.collaborator.TestWatches;
import com.mcpscanner.checks.shared.collaborator.WatchDecision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SettlingLateIssuesTest {

    private final FakeCollaborator collaborator = new FakeCollaborator();
    private final CollaboratorWatch watch = TestWatches.immediate(collaborator);
    private final SettlingLateIssues late = new SettlingLateIssues(watch);
    private final McpCheck check = mock(RequestCheck.class);
    private final McpCheck otherCheck = mock(RequestCheck.class);
    private final AuditIssue issue = mock(AuditIssue.class);

    @Test
    void settlesTheWatchSoAnInteractionAfterTheAuditIsReportedAndReturned() {
        CollaboratorPayload payload = watch.probing(source -> source.newPayload().orElseThrow());
        watch.watch(List.of(payload), observed -> {
            late.report(check, List.of(issue));
            return WatchDecision.DONE;
        });
        collaborator.interact(payload, InteractionType.DNS);

        assertThat(late.settle(check)).containsExactly(issue);
        assertThat(late.settle(check)).isEmpty();
    }

    @Test
    void returnsOnlyTheIssuesOfTheSettledCheck() {
        late.report(otherCheck, List.of(issue));

        assertThat(late.settle(check)).isEmpty();
        assertThat(late.settle(otherCheck)).containsExactly(issue);
    }
}
