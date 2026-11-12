package com.mcpscanner.checks.shared.probe;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.shared.auth.RequestMutation;
import com.mcpscanner.checks.shared.auth.oracle.BaselineEquivalenceOracle;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class NonexistentRouteControlTest {

    private static final RequestMutation NONEXISTENT_CHILD =
            new RequestMutation("/mcp/__mcp_c", request -> request.withPath("/mcp/__mcp_c"));
    private static final HttpRequest SEED = SimulatedMcpServer.request(SimulatedMcpServer.toolsList(), Map.of());

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    private static NonexistentRouteControl controlOn(SimulatedMcpServer server) {
        ProbeChannel channel = new ProbeChannel(server.http(), 10);
        BaselineEquivalenceOracle oracle = BaselineEquivalenceOracle.defaultOracle();
        return new NonexistentRouteControl(new IndependentSessionSender(channel, oracle), oracle);
    }

    @Test
    void aRefusedNonexistentChildLetsTheRouteBeReported() {
        NonexistentRouteControl control = controlOn(SimulatedMcpServer.enforcing());

        assertThat(control.suppresses(control.sendFor(Optional.of(NONEXISTENT_CHILD), SEED))).isFalse();
    }

    @Test
    void aNonexistentChildAnsweredWithAnAppPageSuppressesTheRoute() {
        NonexistentRouteControl control = controlOn(SimulatedMcpServer.enforcing().answeringUnknownPathsWithAnAppPage());

        assertThat(control.suppresses(control.sendFor(Optional.of(NONEXISTENT_CHILD), SEED))).isTrue();
    }

    @Test
    void aRouteWithoutAControlIsNeverSuppressedAndSendsNothing() {
        SimulatedMcpServer server = SimulatedMcpServer.enforcing();
        NonexistentRouteControl control = controlOn(server);

        assertThat(control.suppresses(control.sendFor(Optional.empty(), SEED))).isFalse();
        assertThat(server.received()).isEmpty();
    }
}
