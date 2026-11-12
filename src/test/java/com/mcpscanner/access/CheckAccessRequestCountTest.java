package com.mcpscanner.access;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.access.AccessComparison;
import com.mcpscanner.access.comparison.CrossIdentityComparison;
import com.mcpscanner.access.comparison.CrossIdentityPorts;
import com.mcpscanner.checks.api.PrincipalListings;
import com.mcpscanner.checks.api.crossidentity.CallerServer;
import com.mcpscanner.checks.api.crossidentity.FakeMcpTarget;
import com.mcpscanner.checks.api.crossidentity.FakeReply;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpHeaders;
import com.mcpscanner.scan.ScanTarget;
import com.mcpscanner.testutil.ImmutableHttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.mcpscanner.access.AccessFixtures.ALICE;
import static com.mcpscanner.access.AccessFixtures.BOB;
import static com.mcpscanner.checks.api.crossidentity.FakeMcpTarget.method;
import static com.mcpscanner.testutil.AwaitedFutures.valueWithin;
import static org.assertj.core.api.Assertions.assertThat;

class CheckAccessRequestCountTest {

    private static final String LIVE_SESSION = "primary-session";
    private static final List<String> TOOLS = List.of("a", "b", "c", "d", "e", "f");

    private final AccessFixtures fixtures = new AccessFixtures().compare(BOB);
    private final WriteReplayConfirmations confirmations = new WriteReplayConfirmations();
    private final AccessRuns runs = new AccessRuns(Executors.newSingleThreadExecutor());

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @AfterEach
    void stopRuns() {
        runs.shutdown();
    }

    @Test
    void statelessServerGetsTheOwnerRequestOneIdentityReplayAndOneAnonymousRequestPerItem() {
        FakeMcpTarget target = new FakeMcpTarget(CallerServer.stateless(caller -> FakeReply.result("note")));

        checkAccess(target, Optional.empty());

        assertThat(target.sent()).hasSize(3 * TOOLS.size());
        assertThat(requestsPerTool(target)).containsOnlyKeys(TOOLS).allSatisfy((tool, sent) ->
                assertThat(sent).extracting(FakeMcpTarget::principal)
                        .containsExactlyInAnyOrder("alice-token", "bob-token", null));
    }

    @Test
    void statefulServerGetsOneHandshakePerIdentityPerRunBesidesThreeRequestsPerItem() {
        FakeMcpTarget target = new FakeMcpTarget(
                CallerServer.stateful(caller -> FakeReply.result("note")).admittingAnonymousSessions());

        checkAccess(target, Optional.of(LIVE_SESSION));

        assertThat(target.sent()).filteredOn(request -> "tools/call".equals(method(request)))
                .hasSize(3 * TOOLS.size());
        assertThat(requestsPerTool(target)).allSatisfy((tool, sent) -> assertThat(sent).hasSize(3));
        assertThat(target.sent()).filteredOn(request -> "initialize".equals(method(request)))
                .extracting(FakeMcpTarget::principal).containsExactlyInAnyOrder("bob-token", null);
        assertThat(target.sent()).filteredOn(request -> "notifications/initialized".equals(method(request)))
                .hasSize(2);
        assertThat(target.sent()).filteredOn(request -> "DELETE".equals(request.method()))
                .extracting(FakeMcpTarget::session).hasSize(2).doesNotContain(LIVE_SESSION);
    }

    private void checkAccess(FakeMcpTarget target, Optional<String> liveSession) {
        List<AccessRow> rows = fixtures.lists(ALICE, TOOLS.stream().map(AccessFixtures::readOnlyTool).toList(),
                List.of()).matrix().snapshot().rows();
        MatrixAccessLedger ledger = new MatrixAccessLedger(fixtures.matrix(), confirmations);
        AccessComparison comparison = new CrossIdentityComparison(McpEventLog.noop(),
                () -> new BearerTokenAuthStrategy("alice-token"), fixtures::roster,
                CrossIdentityPorts.of(ledger, PrincipalListings.unknown()));
        OwnerRequests requests = new OwnerRequests(scanTarget -> ownerRequest(scanTarget, liveSession),
                new IdentityRequests(fixtures::roster));
        CompletableFuture<AccessRunSummary> finished = new CompletableFuture<>();

        new CheckAccessRunner(comparison, target.http(), requests, issue -> { },
                new WriteReplays(confirmations, ledger), runs, McpEventLog.noop())
                .start(rows, tools -> true, new AccessRunListener() {
                    @Override
                    public void progress(int done, int total) {
                    }

                    @Override
                    public void finished(AccessRunSummary summary) {
                        finished.complete(summary);
                    }
                }).orElseThrow();

        assertThat(valueWithin(Duration.ofSeconds(10), finished).checked()).isEqualTo(TOOLS.size());
    }

    private static Map<String, List<HttpRequest>> requestsPerTool(FakeMcpTarget target) {
        return target.sent().stream().filter(request -> "tools/call".equals(method(request)))
                .collect(Collectors.groupingBy(FakeMcpTarget::tool));
    }

    private static HttpRequest ownerRequest(ScanTarget target, Optional<String> liveSession) {
        Map<String, String> headers = new LinkedHashMap<>(Map.of("Authorization", "Bearer alice-token",
                "Content-Type", "application/json"));
        liveSession.ifPresent(sessionId -> headers.put(McpHeaders.SESSION_ID, sessionId));
        return ImmutableHttpRequest.post("https://mcp.test/mcp", headers,
                "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\""
                        + target.identifier() + "\",\"arguments\":{}}}");
    }
}
