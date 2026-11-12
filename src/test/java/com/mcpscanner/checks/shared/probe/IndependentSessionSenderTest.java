package com.mcpscanner.checks.shared.probe;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.shared.auth.oracle.BaselineEquivalenceOracle;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IndependentSessionSenderTest {

    private static final HttpRequest ANONYMOUS =
            SimulatedMcpServer.request(SimulatedMcpServer.toolCall(), Map.of("Content-Type", "application/json"));

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    private static IndependentSessionSender sender(SimulatedMcpServer server) {
        return new IndependentSessionSender(new ProbeChannel(server.http(), 100),
                BaselineEquivalenceOracle.defaultOracle());
    }

    @Test
    void statelessReplyIsReturnedAsIs() {
        SimulatedMcpServer server = SimulatedMcpServer.open();

        ProbeExchange exchange = sender(server).send(ANONYMOUS);

        assertThat(exchange.exchanges()).hasSize(1);
        assertThat(exchange.last().response().statusCode()).isEqualTo((short) 200);
    }

    @Test
    void missingSessionOpensAFreshAnonymousSessionAndRetries() {
        SimulatedMcpServer server = SimulatedMcpServer.open().requiringSessions();

        ProbeExchange exchange = sender(server).send(ANONYMOUS);

        assertThat(exchange.exchanges()).hasSize(3);
        assertThat(exchange.last().response().statusCode()).isEqualTo((short) 200);
        assertThat(exchange.last().request().headerValue("Mcp-Session-Id")).isEqualTo("session-1");
        assertThat(exchange.last().request().headerValue("Authorization")).isNull();
        assertThat(server.received()).extracting(HttpRequest::bodyToString)
                .anyMatch(body -> body.contains("notifications/initialized"));
    }

    @Test
    void aHandshakeThatIssuesNoSessionLeavesTheExchangeInconclusive() {
        SimulatedMcpServer server = SimulatedMcpServer.open().requiringSessions().issuingNoSessionIds();

        ProbeExchange exchange = sender(server).send(ANONYMOUS);

        assertThat(exchange.conclusive()).isFalse();
        assertThat(exchange.last().response().statusCode()).isEqualTo((short) 400);
    }

    @Test
    void refusedHandshakeBecomesTheFinalRefusedExchange() {
        SimulatedMcpServer server = SimulatedMcpServer.enforcing();

        ProbeExchange exchange = sender(server).send(ANONYMOUS);

        assertThat(exchange.exchanges()).hasSize(1);
        assertThat(exchange.last().response().statusCode()).isEqualTo((short) 401);
    }

    @Test
    void handshakeWithoutAnIssuedSessionKeepsTheOriginalReply() {
        SimulatedMcpServer server = SimulatedMcpServer.open().requiringSessions().issuingNoSessionIds();

        ProbeExchange exchange = sender(server).send(ANONYMOUS);

        assertThat(exchange.exchanges()).hasSize(1);
        assertThat(exchange.last().response().statusCode()).isEqualTo((short) 400);
        assertThat(server.received()).hasSize(2);
    }

    @Test
    void probesThatAlreadyCarryASessionAreNotRetried() {
        SimulatedMcpServer server = SimulatedMcpServer.open().requiringSessions();

        ProbeExchange exchange = sender(server).send(ANONYMOUS.withHeader("Mcp-Session-Id", "unknown"));

        assertThat(exchange.exchanges()).hasSize(1);
    }

    @Test
    void confirmingARefusalTriesAFreshAnonymousSessionFirst() {
        SimulatedMcpServer server = SimulatedMcpServer.open().requiringSessions().refusingSessionlessRequests();

        assertThat(sender(server).send(ANONYMOUS).last().response().statusCode()).isEqualTo((short) 401);
        assertThat(sender(server).sendConfirmingRefusalInAFreshSession(ANONYMOUS).last().response().statusCode())
                .isEqualTo((short) 200);
    }

    @Test
    void confirmedRefusalStaysRefusedWhenTheHandshakeIsRefusedToo() {
        SimulatedMcpServer server = SimulatedMcpServer.enforcing().requiringSessions();

        assertThat(sender(server).sendConfirmingRefusalInAFreshSession(ANONYMOUS).last().response().statusCode())
                .isEqualTo((short) 401);
    }

    @Test
    void sessionlessServerErrorIsRetriedInAFreshSessionBeforeItCanCountAsARefusal() {
        SimulatedMcpServer server = SimulatedMcpServer.open().requiringSessions().failingWithoutSessions();

        ProbeExchange exchange = sender(server).sendConfirmingRefusalInAFreshSession(ANONYMOUS);

        assertThat(exchange.exchanges()).hasSize(3);
        assertThat(exchange.last().response().statusCode()).isEqualTo((short) 200);
    }
}
