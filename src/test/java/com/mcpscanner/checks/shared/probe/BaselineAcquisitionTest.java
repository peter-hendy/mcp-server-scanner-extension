package com.mcpscanner.checks.shared.probe;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.checks.shared.auth.oracle.BaselineEquivalenceOracle;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BaselineAcquisitionTest {

    private final BaselineEquivalenceOracle oracle = BaselineEquivalenceOracle.defaultOracle();

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    private static BaselineAcquisition acquisitionOn(SimulatedMcpServer server) {
        ProbeChannel channel = new ProbeChannel(server.http(), 10);
        return new BaselineAcquisition(new AuthenticatedBaseline(() -> new BearerTokenAuthStrategy("valid-token")),
                channel, new IndependentSessionSender(channel, BaselineEquivalenceOracle.defaultOracle()),
                BaselineEquivalenceOracle.defaultOracle());
    }

    private HttpRequestResponse acquire(SimulatedMcpServer server, HttpRequestResponse base) {
        return acquisitionOn(server).acquire(base);
    }

    private static HttpRequest seed(Map<String, String> headers) {
        return SimulatedMcpServer.request(SimulatedMcpServer.toolCall(), headers);
    }

    @Test
    void successfulBaseIsKeptWithoutSendingAnything() {
        SimulatedMcpServer server = SimulatedMcpServer.enforcing();
        HttpRequestResponse base = server.exchange(seed(Map.of("Authorization", SimulatedMcpServer.VALID_TOKEN)));

        assertThat(acquire(server, base)).isSameAs(base);
        assertThat(server.received()).hasSize(1);
    }

    @Test
    void failedBaseIsResentWithTheConfiguredCredentials() {
        SimulatedMcpServer server = SimulatedMcpServer.enforcing();
        HttpRequestResponse base = server.exchange(seed(Map.of()));

        HttpRequestResponse baseline = acquire(server, base);

        assertThat(baseline.request().headerValue("Authorization")).isEqualTo(SimulatedMcpServer.VALID_TOKEN);
        assertThat(baseline.response().statusCode()).isEqualTo((short) 200);
    }

    @Test
    void deadLiveSessionIsReplacedByAFreshAuthenticatedSession() {
        SimulatedMcpServer server = SimulatedMcpServer.enforcing().requiringSessions();
        HttpRequestResponse base = server.exchange(seed(Map.of("Mcp-Session-Id", "expired")));

        HttpRequestResponse baseline = acquire(server, base);

        assertThat(baseline.response().statusCode()).isEqualTo((short) 200);
        assertThat(baseline.request().headerValue("Mcp-Session-Id")).isNotEqualTo("expired").isNotNull();
        assertThat(baseline.request().headerValue("Authorization")).isEqualTo(SimulatedMcpServer.VALID_TOKEN);
    }

    @Test
    void aFreshBaselineIsAlwaysSentWithTheConfiguredCredentials() {
        SimulatedMcpServer server = SimulatedMcpServer.enforcing();

        HttpRequestResponse baseline = acquisitionOn(server).acquireFresh(seed(Map.of()));

        assertThat(baseline.request().headerValue("Authorization")).isEqualTo(SimulatedMcpServer.VALID_TOKEN);
        assertThat(baseline.response().statusCode()).isEqualTo((short) 200);
        assertThat(server.received()).hasSize(1);
    }
}
