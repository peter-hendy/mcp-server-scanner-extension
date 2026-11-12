package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.checks.shared.auth.AuthVariantSuite;
import com.mcpscanner.checks.shared.auth.CredentialStripper;
import com.mcpscanner.checks.shared.auth.oracle.BaselineEquivalenceOracle;
import com.mcpscanner.checks.shared.probe.ControlRefusal;
import com.mcpscanner.checks.shared.probe.IndependentSessionSender;
import com.mcpscanner.checks.shared.probe.ProbeChannel;
import com.mcpscanner.checks.shared.probe.ProbeExchange;
import com.mcpscanner.checks.shared.probe.SimulatedMcpServer;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class BypassSweepTest {

    private static final Map<String, String> AUTHENTICATED = Map.of("Authorization", SimulatedMcpServer.VALID_TOKEN);

    private final Supplier<AuthStrategy> strategy = () -> new BearerTokenAuthStrategy("valid-token");
    private final BaselineEquivalenceOracle oracle = BaselineEquivalenceOracle.defaultOracle();
    private final SweepMemo memo = UnauthenticatedRequestExecutionCheck.newSweepMemo();
    private int lastSweepRequests;

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    private Optional<Finding> sweep(SimulatedMcpServer server, int budget) {
        return sweep(server, budget, SimulatedMcpServer.toolCall());
    }

    private Optional<Finding> sweep(SimulatedMcpServer server, int budget, String seedBody) {
        ProbeChannel channel = new ProbeChannel(server.http(), budget);
        IndependentSessionSender sender = new IndependentSessionSender(channel, oracle);
        AuthVariantSuite suite = new AuthVariantSuite(strategy, "canary");
        AnonymousRecheck recheck = new AnonymousRecheck(suite, sender, new ControlRefusal(channel, oracle));
        BypassSweep sweep = new BypassSweep(new SweepVariants(suite,
                new RetainedSessionVariant(new CredentialStripper(strategy)), SweepFamilies.forDirectDelivery()), memo, sender, channel, oracle, recheck);
        HttpRequestResponse baseline = server.exchange(SimulatedMcpServer.request(seedBody, AUTHENTICATED));
        AuditSubject subject = new AuditSubject(baseline, SeedMessage.of(baseline.request()).orElseThrow(), true);
        ProbeExchange refused = ProbeExchange.of(server.exchange(SimulatedMcpServer.request(seedBody, Map.of())));
        int before = server.received().size();
        Optional<Finding> finding = sweep.sweep(subject, List.of(refused));
        lastSweepRequests = server.received().size() - before;
        return finding;
    }

    @Test
    void firstVariantThatReproducesTheBaselineIsReportedWithItsControls() {
        Optional<Finding> finding = sweep(SimulatedMcpServer.enforcing().withUnguardedPath("/MCP"), 500);

        assertThat(finding).hasValueSatisfying(hit -> {
            assertThat(hit.kind()).isEqualTo(FindingKind.AUTHENTICATION_BYPASS);
            assertThat(hit.variantName()).isEqualTo("path.case");
            assertThat(hit.variantValue()).isEqualTo("/MCP");
            assertThat(hit.evidence()).extracting(exchange -> exchange.request().path())
                    .contains("/mcp", "/MCP")
                    .anyMatch(path -> path.toLowerCase().startsWith("/mcp/__mcp_canary"));
            assertThat(hit.evidence()).filteredOn(exchange -> exchange.request().headerValue("Authorization") == null
                            && exchange.request().path().equals("/mcp"))
                    .extracting(exchange -> (int) exchange.response().statusCode()).contains(401);
        });
    }

    @Test
    void aRefusalThatHasClearedByTheTimeOfTheHitIsNotABypass() {
        SimulatedMcpServer server = SimulatedMcpServer.open().refusingTheFirstAnonymousRequests(1);

        assertThat(sweep(server, 500)).isEmpty();
    }

    @Test
    void variantWhoseOwnControlAlsoSucceedsIsNotABypass() {
        assertThat(sweep(SimulatedMcpServer.enforcing().trustingHeader("X-Real-IP").trustingAnyValue(), 500))
                .isEmpty();
    }

    @Test
    void enforcingServerYieldsNothing() {
        assertThat(sweep(SimulatedMcpServer.enforcing(), 500)).isEmpty();
    }

    @Test
    void exhaustedBudgetStopsTheSweep() {
        SimulatedMcpServer server = SimulatedMcpServer.enforcing().withUnguardedPath("/MCP");

        assertThat(sweep(server, 3)).isEmpty();
    }

    @Test
    void aLaterSeedOfTheSameMethodSkipsEveryVariantAlreadyRefused() {
        SimulatedMcpServer server = SimulatedMcpServer.enforcing();
        sweep(server, 500);
        int firstSweep = lastSweepRequests;

        assertThat(sweep(server, 500, otherToolCall())).isEmpty();

        assertThat(firstSweep).isGreaterThan(20);
        assertThat(lastSweepRequests).isZero();
    }

    @Test
    void aLaterSeedOfAnotherMethodSweepsInFull() {
        SimulatedMcpServer server = SimulatedMcpServer.enforcing();
        sweep(SimulatedMcpServer.enforcing(), 500, SimulatedMcpServer.toolsList());
        int unmemoised = lastSweepRequests;
        memo.clear();
        sweep(server, 500);

        sweep(server, 500, SimulatedMcpServer.toolsList());

        assertThat(lastSweepRequests).isEqualTo(unmemoised).isGreaterThan(20);
    }

    @Test
    void aLaterSeedStillFindsTheBypassAfterSkippingSettledRejections() {
        SimulatedMcpServer server = SimulatedMcpServer.enforcing().withUnguardedPath("/MCP");
        sweep(server, 500);
        int firstSweep = lastSweepRequests;

        Optional<Finding> finding = sweep(server, 500, otherToolCall());

        assertThat(finding).hasValueSatisfying(hit -> assertThat(hit.variantName()).isEqualTo("path.case"));
        assertThat(lastSweepRequests).isLessThan(firstSweep);
    }

    @Test
    void aToolTheBypassCallerMayNotUseDoesNotHideTheBypassFromOtherTools() {
        SimulatedMcpServer server = SimulatedMcpServer.enforcing().withUnguardedPath("/MCP")
                .denyingToolToBypassCallers("admin_reset");
        assertThat(sweep(server, 500, SimulatedMcpServer.toolCallOf("admin_reset"))).isEmpty();

        Optional<Finding> finding = sweep(server, 500, SimulatedMcpServer.toolCall());

        assertThat(finding).hasValueSatisfying(hit -> assertThat(hit.variantName()).isEqualTo("path.case"));
    }

    @Test
    void aToolForbiddenAtTheHttpLevelDoesNotHideTheBypassFromOtherTools() {
        SimulatedMcpServer server = SimulatedMcpServer.enforcing().withUnguardedPath("/MCP")
                .forbiddingToolToBypassCallers("admin_export");
        assertThat(sweep(server, 500, SimulatedMcpServer.toolCallOf("admin_export"))).isEmpty();

        Optional<Finding> finding = sweep(server, 500, SimulatedMcpServer.toolCall());

        assertThat(finding).hasValueSatisfying(hit -> assertThat(hit.variantName()).isEqualTo("path.case"));
    }

    private static String otherToolCall() {
        return "{\"jsonrpc\":\"2.0\",\"id\":21,\"method\":\"tools/call\",\"params\":{\"name\":\"read_secret\","
                + "\"arguments\":{\"page\":2}}}";
    }
}
