package com.mcpscanner.access.comparison;

import com.mcpscanner.checks.api.crossidentity.FakeMcpTarget;
import com.mcpscanner.checks.api.crossidentity.FakeReply;
import com.mcpscanner.checks.api.crossidentity.FakeServers;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.api.AccessExpectation;
import com.mcpscanner.checks.api.AccessOutcome;
import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRole;
import com.mcpscanner.mcp.ScannerSentinels;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.mcpscanner.access.comparison.ComparisonHarness.identity;
import static com.mcpscanner.access.comparison.ComparisonHarness.withPeerB;
import static com.mcpscanner.checks.api.crossidentity.FakeMcpTarget.principal;
import static com.mcpscanner.checks.api.crossidentity.FakeMcpTarget.toolCall;
import static org.assertj.core.api.Assertions.assertThat;

class AccessComparisonOutcomeTest {

    private static final HttpRequest GET_NOTE = toolCall("get_note", "{\"id\":\"a1\"}", "A");

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void sameResultAndRefusedAnonymousAreRecorded() {
        var run = withPeerB().run(FakeServers.objectStore(true), GET_NOTE);

        assertThat(run.ledger().outcomes()).containsEntry("b", AccessOutcome.SAME_RESULT)
                .containsEntry(RecordingLedger.ANONYMOUS, AccessOutcome.REFUSED);
    }

    @Test
    void probeDecisionsAreDebugAndOneInfoLineSummarisesTheBaseRequest() {
        var run = withPeerB().run(FakeServers.objectStore(true), GET_NOTE);

        assertThat(run.infoLines()).singleElement().asString()
                .isEqualTo("Check access — tools/call get_note: outcomes anonymous=REFUSED, "
                        + "identity-B=SAME_RESULT; 0 issue(s)");
    }

    @Test
    void anAuthRefusalAtTheIdentitysInitializeIsRecordedRefused() {
        var run = withPeerB().run(FakeServers.refusingPeerInitialize(),
                toolCall("read_session_record", "{}", "A").withAddedHeader("Mcp-Session-Id", "sess-A"));

        assertThat(run.ledger().outcomes()).containsEntry("b", AccessOutcome.REFUSED);
    }

    @Test
    void rateLimitedAnonymousRequestStopsTheAuditWithoutAnyIdentityProbe() {
        var run = new ComparisonHarness().with(identity("C", PrincipalRole.LOWER, Map.of("Authorization", "Bearer C"),
                        Optional.empty(), Map.of()))
                .ledger(new RecordingLedger().expecting(AccessExpectation.NOT_LOWER).listing("c", false))
                .run(request -> principal(request) == null ? new FakeReply(429, "", Map.of())
                        : FakeServers.adminTool(false).apply(request), toolCall("admin_report", "{}", "A"));

        assertThat(run.issues()).isEmpty();
        assertThat(run.sent()).noneMatch(request -> "C".equals(principal(request)));
        assertThat(run.decisions()).contains("anonymous_unanswered");
        assertThat(run.ledger().outcomes()).containsEntry("c", AccessOutcome.NOT_RUN)
                .containsEntry(RecordingLedger.ANONYMOUS, AccessOutcome.ERROR);
    }

    @Test
    void refusalIsRecorded() {
        var run = withPeerB().run(FakeServers.objectStore(false), GET_NOTE);

        assertThat(run.ledger().outcomes()).containsEntry("b", AccessOutcome.REFUSED);
    }

    @Test
    void differentResultIsRecorded() {
        var run = withPeerB().run(FakeServers.whoami(), GET_NOTE);

        assertThat(run.ledger().outcomes()).containsEntry("b", AccessOutcome.DIFFERENT_RESULT);
    }

    @Test
    void anonymousSuccessRecordsTheAnonymousSameResultAndSkipsIdentities() {
        var run = withPeerB().run(FakeServers.open(), GET_NOTE);

        assertThat(run.ledger().outcomes()).containsEntry("b", AccessOutcome.NOT_RUN)
                .containsEntry(RecordingLedger.ANONYMOUS, AccessOutcome.SAME_RESULT);
    }

    @Test
    void transportFailureForAnIdentityIsRecordedAsAnError() {
        var run = withPeerB().run(request -> "B".equals(principal(request)) ? new FakeReply(429, "", Map.of())
                : FakeServers.objectStore(true).apply(request), GET_NOTE);

        assertThat(run.ledger().outcomes()).containsEntry("b", AccessOutcome.ERROR);
    }

    @Test
    void everyIdentityOnAStatefulServerCompletesWithinItsOwnBudget() {
        ComparisonHarness harness = new ComparisonHarness();
        for (String token : List.of("B", "C", "D")) {
            harness.with(identity(token, PrincipalRole.PEER, Map.of("Authorization", "Bearer " + token),
                    Optional.empty(), Map.of()));
        }

        var run = harness.run(FakeServers.ownerOnlySessions(),
                toolCall("read_session_record", "{}", "A").withAddedHeader("Mcp-Session-Id", "sess-A")
                        .withAddedHeader("MCP-Protocol-Version", "2025-11-25"));

        assertThat(run.ledger().outcomes()).containsEntry("b", AccessOutcome.REFUSED)
                .containsEntry("c", AccessOutcome.REFUSED).containsEntry("d", AccessOutcome.REFUSED);
    }

    @Test
    void everyIdentityProbeCarriesItsRoutingSentinel() {
        var run = withPeerB().run(FakeServers.objectStore(true), GET_NOTE);

        assertThat(run.sent()).filteredOn(request -> "B".equals(principal(request)))
                .isNotEmpty()
                .allMatch(request -> "b".equals(request.headerValue(ScannerSentinels.IDENTITY_HEADER)));
        assertThat(run.sent()).filteredOn(request -> principal(request) == null)
                .isNotEmpty()
                .allMatch(request -> Principal.ANONYMOUS_ID.equals(
                        request.headerValue(ScannerSentinels.IDENTITY_HEADER))
                        && request.hasHeader(ScannerSentinels.STRIP_AUTH_HEADER));
    }

    @Test
    void identityProbesNeverCarryThePrimarysAuthorizationEvenWhenTheBaseDoes() {
        HttpRequest seed = toolCall("read_session_record", "{}", "A").withAddedHeader("Mcp-Session-Id", "sess-A")
                .withAddedHeader("X-Api-Key", "primary-key");

        var run = new ComparisonHarness().with(identity("K", PrincipalRole.PEER, Map.of("X-Api-Key", "k-key"),
                        Optional.empty(), Map.of()))
                .with(ComparisonHarness.peer("B"))
                .run(FakeServers.sessions(false), seed);

        assertThat(run.sent()).filteredOn(request -> !Principal.PRIMARY_ID.equals(
                        request.headerValue(ScannerSentinels.IDENTITY_HEADER))
                        && request.hasHeader(ScannerSentinels.IDENTITY_HEADER))
                .isNotEmpty()
                .allSatisfy(request -> {
                    assertThat(request.headers()).noneMatch(header -> header.value().equals("Bearer A"));
                    assertThat(request.headers()).noneMatch(header -> header.value().equals("primary-key"));
                });
        assertThat(run.sent()).filteredOn(request -> "k".equals(request.headerValue(ScannerSentinels.IDENTITY_HEADER)))
                .isNotEmpty()
                .allMatch(request -> request.headerValue("Authorization") == null
                        && "k-key".equals(request.headerValue("X-Api-Key")));
    }

    @Test
    void stateLessTransportsOpenNoSessions() {
        var run = withPeerB().run(FakeServers.objectStore(true), GET_NOTE);

        assertThat(run.sent()).noneMatch(request -> "initialize".equals(FakeMcpTarget.method(request)));
        assertThat(run.decisions()).noneMatch(decision -> decision.startsWith("session_"));
    }

    @Test
    void everyFindingIsRecordedAgainstTheIdentityThatReachedTheResult() {
        var run = withPeerB().ownerMarker("A-PRIVATE-a1").run(FakeServers.objectStore(true), GET_NOTE);

        assertThat(run.ledger().findings()).containsExactly("b");
    }

    @Test
    void baseRequestRoutedAsAComparedIdentityMakesThatIdentityTheOwner() {
        HttpRequest routedAsHigher = toolCall("get_note", "{\"id\":\"a1\"}", "H")
                .withHeader(ScannerSentinels.IDENTITY_HEADER, "h");
        var run = withPeerB().with(identity("H", PrincipalRole.HIGHER, Map.of("Authorization", "Bearer H"),
                Optional.empty(), Map.of())).run(FakeServers.objectStore(true), routedAsHigher);

        assertThat(run.ledger().outcomes()).containsKeys("primary", "b").doesNotContainKey("h");
        assertThat(run.sent()).noneMatch(request -> "H".equals(principal(request)));
    }

    @Test
    void everyProbeSessionTheCheckOpenedIsDeletedAndTheLiveSessionNever() {
        HttpRequest seed = toolCall("read_session_record", "{}", "A").withAddedHeader("Mcp-Session-Id", "sess-A");

        var run = withPeerB().run(FakeServers.sessions(false), seed);

        List<String> used = run.sent().stream().filter(request -> !"DELETE".equals(request.method()))
                .map(request -> request.headerValue("Mcp-Session-Id"))
                .filter(id -> id != null && !id.equals("sess-A")).distinct().toList();
        List<String> deleted = run.sent().stream().filter(request -> "DELETE".equals(request.method()))
                .map(request -> request.headerValue("Mcp-Session-Id")).toList();
        assertThat(used).isNotEmpty();
        assertThat(deleted).containsExactlyInAnyOrderElementsOf(used);
        assertThat(run.sent().subList(run.sent().size() - deleted.size(), run.sent().size()))
                .allMatch(request -> "DELETE".equals(request.method()));
    }
}
