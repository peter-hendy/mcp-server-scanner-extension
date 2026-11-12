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
import com.mcpscanner.checks.shared.probe.SimulatedMcpServer;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionAuditTest {

    private static final Map<String, String> AUTHENTICATED = Map.of("Authorization", SimulatedMcpServer.VALID_TOKEN);

    private final Supplier<AuthStrategy> strategy = () -> new BearerTokenAuthStrategy("valid-token");
    private final BaselineEquivalenceOracle oracle = BaselineEquivalenceOracle.defaultOracle();

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    private Optional<Finding> run(SimulatedMcpServer server, String body) {
        ProbeChannel channel = new ProbeChannel(server.http(), 500);
        IndependentSessionSender sender = new IndependentSessionSender(channel, oracle);
        AuthVariantSuite suite = new AuthVariantSuite(strategy, "canary");
        ControlRefusal controlRefusal = new ControlRefusal(channel, oracle);
        AnonymousRecheck recheck = new AnonymousRecheck(suite, sender, controlRefusal);
        BypassSweep sweep = new BypassSweep(new SweepVariants(suite,
                new RetainedSessionVariant(new CredentialStripper(strategy)), SweepFamilies.forDirectDelivery()),
                UnauthenticatedRequestExecutionCheck.newSweepMemo(), sender, channel, oracle, recheck);
        HttpRequestResponse baseline = server.exchange(SimulatedMcpServer.request(body, AUTHENTICATED));
        AuditSubject subject = new AuditSubject(baseline, SeedMessage.of(baseline.request()).orElseThrow(), true);
        EnforcementEvidence enforcement = new EnforcementEvidence(oracle);
        return new ExecutionAudit(suite, sender, controlRefusal,
                new AnonymousExecutionTier(oracle, enforcement, new EnforcementProbe(channel, suite)),
                new InvalidCredentialTier(suite, sender, recheck, oracle), sweep, enforcement).run(subject);
    }

    @Test
    void anonymousSuccessStopsBeforeTheMalformedControlAndTheSweep() {
        SimulatedMcpServer server = SimulatedMcpServer.open();

        assertThat(run(server, SimulatedMcpServer.toolCall()).orElseThrow().kind())
                .isEqualTo(FindingKind.ANONYMOUS_EXECUTION);
        assertThat(server.received()).extracting(request -> request.headerValue("Authorization"))
                .doesNotContain("Bearer not_a_real_token_12345");
        assertThat(server.received()).extracting(request -> request.pathWithoutQuery()).allMatch(path ->
                path.equals("/mcp") || path.startsWith("/.well-known/oauth-protected-resource"));
    }

    @Test
    void openProtocolMethodIsNotSweptBecauseItsControlIsNotRefused() {
        SimulatedMcpServer server = SimulatedMcpServer.open();

        assertThat(run(server, SimulatedMcpServer.ping())).isEmpty();
        assertThat(server.received()).hasSize(2);
    }

    @Test
    void bothControlsRefusedLeadsToTheSweep() {
        SimulatedMcpServer server = SimulatedMcpServer.enforcing().withUnguardedPath("/MCP");

        assertThat(run(server, SimulatedMcpServer.toolCall()).orElseThrow().kind())
                .isEqualTo(FindingKind.AUTHENTICATION_BYPASS);
        assertThat(server.received().get(1).headerValue("Authorization")).isNull();
        assertThat(server.received().get(2).bodyToString()).contains("\"initialize\"");
        assertThat(server.received().get(3).headerValue("Authorization")).isEqualTo("Bearer not_a_real_token_12345");
    }

    @Test
    void acceptedMalformedCredentialGoesToTheInvalidCredentialTierInsteadOfTheSweep() {
        SimulatedMcpServer server = SimulatedMcpServer.enforcing().acceptingAnyCredential().withUnguardedPath("/MCP");

        assertThat(run(server, SimulatedMcpServer.toolCall()).orElseThrow().kind())
                .isEqualTo(FindingKind.INVALID_CREDENTIAL_ACCEPTED);
        assertThat(server.received()).extracting(request -> request.pathWithoutQuery()).doesNotContain("/MCP");
    }
}
