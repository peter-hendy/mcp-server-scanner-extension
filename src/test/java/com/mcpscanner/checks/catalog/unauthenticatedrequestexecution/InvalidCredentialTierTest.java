package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.checks.shared.auth.AuthVariantSuite;
import com.mcpscanner.checks.shared.auth.oracle.BaselineEquivalenceOracle;
import com.mcpscanner.checks.shared.probe.ControlRefusal;
import com.mcpscanner.checks.shared.probe.IndependentSessionSender;
import com.mcpscanner.checks.shared.probe.ProbeChannel;
import com.mcpscanner.checks.shared.probe.ProbeExchange;
import com.mcpscanner.checks.shared.probe.SimulatedMcpServer;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InvalidCredentialTierTest {

    private static final Map<String, String> AUTHENTICATED = Map.of("Authorization", SimulatedMcpServer.VALID_TOKEN);
    private static final Map<String, String> MALFORMED = Map.of("Authorization", "Bearer not_a_real_token_12345");

    private final BaselineEquivalenceOracle oracle = BaselineEquivalenceOracle.defaultOracle();

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    private Optional<Finding> judge(SimulatedMcpServer server, String body, boolean credentialsConfigured) {
        AuthVariantSuite suite = new AuthVariantSuite(() -> new BearerTokenAuthStrategy("valid-token"), "canary");
        ProbeChannel channel = new ProbeChannel(server.http(), 50);
        IndependentSessionSender sender = new IndependentSessionSender(channel, oracle);
        HttpRequestResponse baseline = server.exchange(SimulatedMcpServer.request(body, AUTHENTICATED));
        AuditSubject subject = new AuditSubject(baseline, SeedMessage.of(baseline.request()).orElseThrow(),
                credentialsConfigured);
        ProbeExchange anonymous = ProbeExchange.of(server.exchange(SimulatedMcpServer.request(body, Map.of())));
        ProbeExchange malformed = ProbeExchange.of(server.exchange(SimulatedMcpServer.request(body, MALFORMED)));
        AnonymousRecheck recheck = new AnonymousRecheck(suite, sender, new ControlRefusal(channel, oracle));
        return new InvalidCredentialTier(suite, sender, recheck, oracle).judge(subject, anonymous, malformed);
    }

    @Test
    void reportsBothFabricatedCredentialsAndRecordsTheRepeatedAnonymousRefusal() {
        Optional<Finding> finding = judge(SimulatedMcpServer.enforcing().acceptingAnyCredential(),
                SimulatedMcpServer.toolCall(), true);

        assertThat(finding).hasValueSatisfying(hit -> {
            assertThat(hit.kind()).isEqualTo(FindingKind.INVALID_CREDENTIAL_ACCEPTED);
            assertThat(hit.variantName()).isEqualTo("credential.garbage-bearer, credential.fabricated-bearer");
            assertThat(hit.variantValue()).isEqualTo("Bearer not_a_real_token_12345, Bearer invalid_canary");
            assertThat(hit.evidence().get(0).request().headerValue("Authorization"))
                    .isEqualTo(SimulatedMcpServer.VALID_TOKEN);
            assertThat(hit.evidence().get(hit.evidence().size() - 1).response().statusCode()).isEqualTo((short) 401);
        });
    }

    @Test
    void aServerThatNowAnswersAnonymousCallersIsNotReportedHere() {
        assertThat(judge(SimulatedMcpServer.open(), SimulatedMcpServer.toolCall(), true)).isEmpty();
    }

    @Test
    void scansWithoutConfiguredCredentialsAreNotJudged() {
        assertThat(judge(SimulatedMcpServer.enforcing().acceptingAnyCredential(), SimulatedMcpServer.toolCall(), false))
                .isEmpty();
    }

    @Test
    void protocolMethodsAreNotJudged() {
        assertThat(judge(SimulatedMcpServer.enforcing().acceptingAnyCredential(), SimulatedMcpServer.ping(), true))
                .isEmpty();
    }

    @Test
    void anOperationWhoseResultIsAnEmbeddedErrorIsNotReported() {
        SimulatedMcpServer server = SimulatedMcpServer.enforcing().acceptingAnyCredential()
                .answeringOperationsWith("{\"data\":\"{\\\"error\\\":\\\"No object ID provided\\\"}\"}");

        assertThat(judge(server, SimulatedMcpServer.toolCall(), true)).isEmpty();
        assertThat(server.received()).hasSize(3);
    }
}
