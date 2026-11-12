package com.mcpscanner.access.comparison;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import com.mcpscanner.checks.api.AccessFinding;
import com.mcpscanner.checks.api.AccessOutcome;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRole;
import com.mcpscanner.checks.api.crossidentity.FakeMcpTarget;
import com.mcpscanner.checks.api.crossidentity.FakeServers;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static com.mcpscanner.checks.api.crossidentity.FakeMcpTarget.toolCall;
import static org.assertj.core.api.Assertions.assertThat;

class PeerReplayTest {

    private static final HttpRequest GET_NOTE = toolCall("get_note", "{\"id\":\"a1\",\"format\":\"md\"}", "A");

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    private static Principal owner(Optional<String> marker, Map<String, String> owned) {
        return new Principal(Principal.PRIMARY_ID, Principal.PRIMARY_NAME, PrincipalRole.PRIMARY,
                () -> Map.of("Authorization", "Bearer A"), marker, owned);
    }

    private static ComparisonHarness owning(Map<String, String> owned) {
        return ComparisonHarness.withPeerB().owner(owner(Optional.empty(), owned));
    }

    @Test
    void theOwnersResultForACallCarryingTheOwnersValueIsAPossibleFindingWhenTheSameShapeControlIsRefused() {
        var run = owning(Map.of("id", "a1")).run(FakeServers.objectStore(true), GET_NOTE);

        assertThat(run.issues()).isEmpty();
        assertThat(run.ledger().findingsOf("b")).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(AccessFinding.Kind.POSSIBLE_OBJECT_LEVEL);
            assertThat(finding.summary()).isEqualTo("Object-level: identity-B received Primary's result for the "
                    + "same arguments (Tentative — no private marker configured for Primary)");
        });
    }

    @Test
    void aControlTheIdentityIsAlsoServedIsNotSeparatedAndRecordsNothing() {
        var run = owning(Map.of("id", "a1")).run(FakeServers.selectorIgnoredForPeer(), GET_NOTE);

        assertThat(run.issues()).isEmpty();
        assertThat(run.ledger().findingsOf("b")).isEmpty();
        assertThat(run.decisions()).contains("peer_replay:triplet_not_separated");
    }

    @Test
    void aControlTheOwnerIsAlsoServedGivesNoSelectorAndRecordsNothing() {
        var run = owning(Map.of("id", "a1")).run(FakeServers.anyIdServesTheNote(), GET_NOTE);

        assertThat(run.ledger().findingsOf("b")).isEmpty();
        assertThat(run.decisions()).contains("peer_replay:no_object_selector");
    }

    @Test
    void aCallCarryingNoValueTheOwnerOwnsIsNotObjectCompared() {
        var run = owning(Map.of("id", "other")).run(FakeServers.objectStore(true), GET_NOTE);

        assertThat(run.ledger().findingsOf("b")).isEmpty();
        assertThat(run.ledger().outcomes()).containsEntry("b", AccessOutcome.SAME_RESULT);
        assertThat(run.decisions()).contains("peer_replay:no_object_selector");
    }

    @Test
    void theControlChangesOnlyTheOwnedArgumentToAnInventedValueOfTheSameShape() {
        var run = owning(Map.of("id", "a1")).run(FakeServers.objectStore(true), GET_NOTE);

        assertThat(run.sent()).filteredOn(request -> !"a1".equals(FakeMcpTarget.argument(request, "id")))
                .isNotEmpty()
                .allSatisfy(request -> {
                    assertThat(FakeMcpTarget.argument(request, "id")).matches("[a-z][0-9]").isNotEqualTo("a1");
                    assertThat(FakeMcpTarget.argument(request, "format")).isEqualTo("md");
                });
    }

    @Test
    void withTheOwnersMarkerTheLeakIsOneFirmIssue() {
        var run = ComparisonHarness.withPeerB().owner(owner(Optional.of("A-PRIVATE-a1"), Map.of("id", "a1")))
                .run(FakeServers.objectStore(true), GET_NOTE);

        assertThat(run.issues()).singleElement().extracting(issue -> issue.confidence())
                .isEqualTo(AuditIssueConfidence.FIRM);
        assertThat(run.ledger().findingsOf("b")).extracting(AccessFinding::kind)
                .containsExactly(AccessFinding.Kind.OBJECT_LEVEL);
    }
}
