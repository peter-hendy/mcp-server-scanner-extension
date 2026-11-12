package com.mcpscanner.access.comparison;

import com.mcpscanner.checks.api.crossidentity.FakeMcpTarget;
import com.mcpscanner.checks.api.crossidentity.FakeReply;
import com.mcpscanner.checks.api.crossidentity.FakeServers;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.api.AccessExpectation;
import com.mcpscanner.checks.api.AccessOutcome;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRole;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.mcpscanner.access.comparison.ComparisonHarness.identity;
import static com.mcpscanner.checks.api.crossidentity.FakeMcpTarget.principal;
import static com.mcpscanner.checks.api.crossidentity.FakeMcpTarget.toolCall;
import static org.assertj.core.api.Assertions.assertThat;

class AccessComparisonOperationLevelTest {

    private static final String OPERATION = FindingKind.OPERATION_AVAILABLE.issueName();
    private static final HttpRequest ADMIN_CALL = toolCall("admin_report", "{}", "A");

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    private static Principal as(String token, PrincipalRole role) {
        return identity(token, role, Map.of("Authorization", "Bearer " + token), Optional.empty(), Map.of());
    }

    private static ComparisonHarness.Run run(AccessExpectation expectation, Principal principal,
                                        Function<HttpRequest, FakeReply> server) {
        return new ComparisonHarness().with(principal).ledger(new RecordingLedger().expecting(expectation))
                .run(server, ADMIN_CALL);
    }

    @ParameterizedTest
    @CsvSource({
            "ANY_SIGNED_IN,LOWER,false", "ANY_SIGNED_IN,PEER,false", "ANY_SIGNED_IN,HIGHER,false",
            "NOT_LOWER,LOWER,true", "NOT_LOWER,PEER,false", "NOT_LOWER,HIGHER,false",
            "OWNER_ONLY,LOWER,true", "OWNER_ONLY,PEER,true", "OWNER_ONLY,HIGHER,true",
            "HIGHER_ONLY,LOWER,true", "HIGHER_ONLY,PEER,true", "HIGHER_ONLY,HIGHER,false",
            "SHARED_BY_DESIGN,LOWER,false", "SHARED_BY_DESIGN,PEER,false", "SHARED_BY_DESIGN,HIGHER,false"})
    void expectationDecidesWhichRolesAreDisallowed(AccessExpectation expectation, PrincipalRole role, boolean fires) {
        var run = run(expectation, as("C", role), FakeServers.adminTool(false));

        assertThat(run.issues().stream().anyMatch(issue -> issue.name().equals(OPERATION))).isEqualTo(fires);
    }

    @ParameterizedTest
    @CsvSource({"NOT_LOWER,LOWER", "OWNER_ONLY,PEER", "HIGHER_ONLY,LOWER"})
    void enforcedOperationIsSilentForEveryDisallowedRole(AccessExpectation expectation, PrincipalRole role) {
        var run = run(expectation, as("C", role), FakeServers.adminTool(true));

        assertThat(run.issues()).isEmpty();
        assertThat(run.ledger().outcomes()).containsEntry("c", AccessOutcome.REFUSED);
    }

    @ParameterizedTest
    @CsvSource({"NOT_LOWER,LOWER,lower privilege", "OWNER_ONLY,PEER,peer", "OWNER_ONLY,HIGHER,higher privilege"})
    void operationFindingNamesTheComparedIdentityWithItsRoleLabel(AccessExpectation expectation, PrincipalRole role,
                                                                  String label) {
        var run = run(expectation, as("C", role), FakeServers.adminTool(false));

        assertThat(run.issues()).singleElement().extracting(issue -> issue.detail()).asString()
                .contains("Identity: identity-C (" + label + ").");
    }

    @Test
    void operationFindingIsHighAndTentativeWhenListedAndUnmarked() {
        var run = run(AccessExpectation.NOT_LOWER, as("C", PrincipalRole.LOWER), FakeServers.adminTool(false));

        assertThat(run.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.name()).isEqualTo(OPERATION);
            assertThat(issue.severity()).isEqualTo(AuditIssueSeverity.HIGH);
            assertThat(issue.confidence()).isEqualTo(AuditIssueConfidence.TENTATIVE);
        });
    }

    @Test
    void readReplayIsNotDescribedAsRepeatingAStateChange() {
        var run = new ComparisonHarness().with(as("C", PrincipalRole.LOWER))
                .ledger(new RecordingLedger().expecting(AccessExpectation.NOT_LOWER).replayingReads())
                .run(FakeServers.adminTool(false), ADMIN_CALL);

        assertThat(run.issues()).singleElement().extracting(issue -> issue.detail()).asString()
                .contains("replayed as supplied for every arm.").doesNotContain("state-changing");
    }

    @Test
    void confirmedWriteReplayIsDescribedAsRepeatingAStateChange() {
        var run = run(AccessExpectation.NOT_LOWER, as("C", PrincipalRole.LOWER), FakeServers.adminTool(false));

        assertThat(run.issues()).singleElement().extracting(issue -> issue.detail()).asString()
                .contains("so a state-changing operation was repeated.");
    }

    @Test
    void operationFindingIsFirmWhenTheItemIsAbsentFromTheIdentitysOwnListing() {
        var run = new ComparisonHarness().with(as("C", PrincipalRole.LOWER))
                .ledger(new RecordingLedger().expecting(AccessExpectation.NOT_LOWER).listing("c", false))
                .run(FakeServers.adminTool(false), ADMIN_CALL);

        assertThat(run.issues()).singleElement().extracting(issue -> issue.confidence())
                .isEqualTo(AuditIssueConfidence.FIRM);
    }

    @Test
    void operationFindingStaysTentativeWhenTheItemIsListedForTheIdentity() {
        var run = new ComparisonHarness().with(as("C", PrincipalRole.LOWER))
                .ledger(new RecordingLedger().expecting(AccessExpectation.NOT_LOWER).listing("c", true))
                .run(FakeServers.adminTool(false), ADMIN_CALL);

        assertThat(run.issues()).singleElement().extracting(issue -> issue.confidence())
                .isEqualTo(AuditIssueConfidence.TENTATIVE);
    }

    @Test
    void operationFindingIsFirmWhenTheOwnersMarkerAppears() {
        var run = new ComparisonHarness().ownerMarker("ADMIN-EFFECT").with(as("C", PrincipalRole.LOWER))
                .ledger(new RecordingLedger().expecting(AccessExpectation.NOT_LOWER))
                .run(FakeServers.adminTool(false), ADMIN_CALL);

        assertThat(run.issues()).singleElement().extracting(issue -> issue.confidence())
                .isEqualTo(AuditIssueConfidence.FIRM);
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void lowerIdentityRefusedAtTheCapturedVersionButAcceptedAtTheLegacyVersionIsReported(boolean stateful) {
        HttpRequest seed = ADMIN_CALL.withAddedHeader("MCP-Protocol-Version", "2025-11-25");
        HttpRequest base = stateful ? seed.withAddedHeader("Mcp-Session-Id", "sess-A") : seed;
        Function<HttpRequest, FakeReply> server = request -> "initialize".equals(FakeMcpTarget.method(request))
                ? FakeReply.initialized("sess-" + FakeMcpTarget.principal(request))
                : "notifications/initialized".equals(FakeMcpTarget.method(request)) ? FakeReply.accepted()
                : FakeServers.versionDependentScope(false).apply(request);

        var run = new ComparisonHarness().with(as("C", PrincipalRole.LOWER))
                .ledger(new RecordingLedger().expecting(AccessExpectation.NOT_LOWER).listing("c", false))
                .run(server, base);

        assertThat(run.decisions()).contains("peer_replay:peer_denied", "peer_replay:accepted_at_protocol_2024-11-05");
        assertThat(run.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.name()).isEqualTo(OPERATION);
            assertThat(issue.confidence()).isEqualTo(AuditIssueConfidence.FIRM);
            assertThat(issue.detail()).contains("2024-11-05");
        });
        assertThat(run.ledger().outcomes()).containsEntry("c", AccessOutcome.SAME_RESULT);
    }

    @Test
    void lowerIdentityAcceptedAtTheLegacyVersionIsNotReportedWhenAnonymousIsAlsoAccepted() {
        HttpRequest seed = ADMIN_CALL.withAddedHeader("MCP-Protocol-Version", "2025-11-25");

        var run = new ComparisonHarness().with(as("C", PrincipalRole.LOWER))
                .ledger(new RecordingLedger().expecting(AccessExpectation.NOT_LOWER))
                .run(FakeServers.legacyHandlerOpen(), seed);

        assertThat(run.issues()).isEmpty();
        assertThat(run.decisions()).contains("peer_replay:anonymous_success_at_protocol_2024-11-05");
    }

    @Test
    void ownerMarkerInADifferentResultNeverCreatesAnOperationFinding() {
        var run = new ComparisonHarness().ownerMarker("A-PRIVATE-a1").with(as("C", PrincipalRole.LOWER))
                .ledger(new RecordingLedger().expecting(AccessExpectation.NOT_LOWER).listing("c", false))
                .run(FakeServers.peerSeesOwnerMarker(), toolCall("get_note", "{\"id\":\"a1\"}", "A"));

        assertThat(run.issues()).noneMatch(issue -> issue.name().equals(OPERATION));
        assertThat(run.ledger().outcomes()).containsEntry("c", AccessOutcome.DIFFERENT_RESULT);
    }

    @Test
    void verticalFindingSuppressesObjectProbesForThatIdentity() {
        var run = new ComparisonHarness().with(as("B", PrincipalRole.PEER))
                .ledger(new RecordingLedger().expecting(AccessExpectation.OWNER_ONLY))
                .run(FakeServers.objectStore(true), toolCall("get_note", "{\"id\":\"a1\"}", "A"));

        assertThat(run.issues()).singleElement().extracting(issue -> issue.name()).isEqualTo(OPERATION);
    }

    @Test
    void sharedByDesignSilencesEveryFindingButStillRecordsOutcomes() {
        var run = new ComparisonHarness().with(as("B", PrincipalRole.PEER))
                .ledger(new RecordingLedger().expecting(AccessExpectation.SHARED_BY_DESIGN))
                .run(FakeServers.objectStore(true), toolCall("get_note", "{\"id\":\"a1\"}", "A"));

        assertThat(run.issues()).isEmpty();
        assertThat(run.ledger().outcomes()).containsEntry("b", AccessOutcome.SAME_RESULT);
    }

    @Test
    void higherIdentityIsNeverObjectCompared() {
        var run = new ComparisonHarness().with(as("H", PrincipalRole.HIGHER))
                .run(FakeServers.objectStore(true), toolCall("get_note", "{\"id\":\"a1\"}", "A"));

        assertThat(run.issues()).isEmpty();
    }

    @Test
    void higherOnlyItemOwnedByAHigherIdentityReportsThePrimary() {
        Principal higher = as("H", PrincipalRole.HIGHER);
        Function<HttpRequest, FakeReply> server = FakeServers.authenticated(request -> "C".equals(principal(request))
                ? FakeReply.toolError("insufficient scope") : FakeReply.result("ADMIN-EFFECT report"));

        Principal alice = new Principal(Principal.PRIMARY_ID, "alice", PrincipalRole.PRIMARY,
                () -> Map.of("Authorization", "Bearer A"), Optional.empty(), Map.of());

        var run = new ComparisonHarness().owner(alice).with(higher).with(as("C", PrincipalRole.LOWER)).auditAs(higher)
                .ledger(new RecordingLedger().expecting(AccessExpectation.HIGHER_ONLY))
                .run(server, toolCall("admin_report", "{}", "H"));

        assertThat(run.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.name()).isEqualTo(OPERATION);
            assertThat(issue.detail()).contains("Identity: alice (Primary user)").doesNotContain("primary");
        });
        assertThat(run.ledger().outcomes()).containsEntry(Principal.PRIMARY_ID, AccessOutcome.SAME_RESULT)
                .containsEntry("c", AccessOutcome.REFUSED).doesNotContainKey("h");
    }

    @Test
    void anonymousServedAsAnIdentityBlocksTheVerticalFinding() {
        var run = new ComparisonHarness().with(as("B", PrincipalRole.PEER))
                .ledger(new RecordingLedger().expecting(AccessExpectation.OWNER_ONLY))
                .run(FakeServers.processWideCache(), toolCall("list_orgs", "{}", "A").withAddedHeader("Mcp-Session-Id", "sess-A"));

        assertThat(run.issues()).noneMatch(issue -> issue.name().equals(OPERATION));
    }
}
