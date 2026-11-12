package com.mcpscanner.checks.shared.probe;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.shared.auth.oracle.BaselineEquivalenceOracle;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ControlRefusalTest {

    private static final Map<String, String> AUTHENTICATED = Map.of("Authorization", SimulatedMcpServer.VALID_TOKEN);
    private static final HttpRequest ANONYMOUS = SimulatedMcpServer.request(SimulatedMcpServer.toolCall(), Map.of());

    private final BaselineEquivalenceOracle oracle = BaselineEquivalenceOracle.defaultOracle();

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    private Optional<ProbeExchange> confirm(SimulatedMcpServer server, HttpRequestResponse baseline) {
        return new ControlRefusal(new ProbeChannel(server.http(), 10), oracle)
                .confirm(baseline, ProbeExchange.of(server.exchange(ANONYMOUS)));
    }

    private static HttpRequestResponse baselineOf(SimulatedMcpServer server) {
        return server.exchange(SimulatedMcpServer.request(SimulatedMcpServer.toolCall(), AUTHENTICATED));
    }

    @Test
    void anAuthRefusalIsConfirmedWithoutAnotherRequest() {
        SimulatedMcpServer server = SimulatedMcpServer.enforcing();
        HttpRequestResponse baseline = baselineOf(server);

        assertThat(confirm(server, baseline)).hasValueSatisfying(control -> assertThat(control.exchanges()).hasSize(1));
        assertThat(server.received()).hasSize(2);
    }

    @Test
    void aServerErrorThatReproducesWhileTheBaselineSucceedsIsARefusal() {
        SimulatedMcpServer server = SimulatedMcpServer.enforcing().failingWithoutCredentials();
        HttpRequestResponse baseline = baselineOf(server);

        assertThat(confirm(server, baseline)).hasValueSatisfying(control -> {
            assertThat(control.exchanges()).hasSize(2);
            assertThat(control.last().response().statusCode()).isEqualTo((short) 500);
        });
    }

    @Test
    void aServerErrorThatChangesOnRepeatIsInconclusive() {
        SimulatedMcpServer server = SimulatedMcpServer.enforcing().failingWithoutCredentialsVariably();

        assertThat(confirm(server, baselineOf(server))).isEmpty();
    }

    @Test
    void aServerErrorIsNotARefusalWhenTheBaselineAlsoFailed() {
        SimulatedMcpServer server = SimulatedMcpServer.enforcing().failingWithoutCredentials();
        HttpRequestResponse failedBaseline = server.exchange(ANONYMOUS);
        int before = server.received().size();

        assertThat(confirm(server, failedBaseline)).isEmpty();
        assertThat(server.received()).hasSize(before + 1);
    }

    @Test
    void aBlockPageWithoutAJsonRpcReplyIsNotARefusal() {
        SimulatedMcpServer server = SimulatedMcpServer.enforcing().blockingAnonymousCallersWithAPage();

        assertThat(confirm(server, baselineOf(server))).isEmpty();
    }

    @Test
    void anOrdinaryFailureIsNotARefusal() {
        SimulatedMcpServer server = SimulatedMcpServer.open().requiringSessions();

        assertThat(confirm(server, baselineOf(server))).isEmpty();
    }
}
