package com.mcpscanner.checks.catalog.crossclientresponseleak;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.checks.catalog.crossclientresponseleak.TrialOutcome.Verdict;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HandshakeRefusalTest {

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void aModernBaseWhoseHandshakeIsRefusedMeetsAModernOnlyServer() {
        assertThat(HandshakeRefusal.verdict(modernBase(), answered(400))).isEqualTo(Verdict.MODERN_ONLY_SERVER);
        assertThat(HandshakeRefusal.verdict(modernBase(), answered(404))).isEqualTo(Verdict.MODERN_ONLY_SERVER);
    }

    @Test
    void anInitializeAnsweredWithAnUnsupportedVersionErrorMeetsAModernOnlyServer() {
        HttpRequestResponse unsupported = HttpRequestResponse.httpRequestResponse(handshake(),
                HttpResponse.httpResponse("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n"
                        + "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32602,"
                        + "\"message\":\"Unsupported protocol version\"}}"));

        assertThat(HandshakeRefusal.verdict(modernBase(), unsupported)).isEqualTo(Verdict.MODERN_ONLY_SERVER);
    }

    @Test
    void aRefusalAfterTheInitializeOpensNoProbeSession() {
        HttpRequestResponse initialized = HttpRequestResponse.httpRequestResponse(
                handshake().withBody("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"),
                refusal(400));

        assertThat(HandshakeRefusal.verdict(modernBase(), initialized)).isEqualTo(Verdict.NO_PROBE_SESSION);
    }

    @Test
    void aRefusedInitializeThatIssuedASessionOpensNoProbeSession() {
        HttpRequestResponse issued = HttpRequestResponse.httpRequestResponse(handshake(),
                HttpResponse.httpResponse("HTTP/1.1 400 Bad Request\r\nMcp-Session-Id: s-1\r\n"
                        + "Content-Length: 0\r\n\r\n"));

        assertThat(HandshakeRefusal.verdict(modernBase(), issued)).isEqualTo(Verdict.NO_PROBE_SESSION);
    }

    @Test
    void aHandshakeNeedingCredentialsOpensNoProbeSession() {
        assertThat(HandshakeRefusal.verdict(modernBase(), answered(401))).isEqualTo(Verdict.NO_PROBE_SESSION);
        assertThat(HandshakeRefusal.verdict(modernBase(), answered(403))).isEqualTo(Verdict.NO_PROBE_SESSION);
    }

    @Test
    void aRateLimitedHandshakeIsRateLimited() {
        assertThat(HandshakeRefusal.verdict(modernBase(), answered(429))).isEqualTo(Verdict.RATE_LIMITED);
        assertThat(HandshakeRefusal.verdict(initializeBasedBase(), answered(429))).isEqualTo(Verdict.RATE_LIMITED);
    }

    @Test
    void anUnansweredHandshakeOpensNoProbeSession() {
        assertThat(HandshakeRefusal.verdict(modernBase(), null)).isEqualTo(Verdict.NO_PROBE_SESSION);
        assertThat(HandshakeRefusal.verdict(modernBase(), HttpRequestResponse.httpRequestResponse(handshake(), null)))
                .isEqualTo(Verdict.NO_PROBE_SESSION);
        assertThat(HandshakeRefusal.verdict(modernBase(), answered(500))).isEqualTo(Verdict.NO_PROBE_SESSION);
    }

    @Test
    void anInitializeBasedBaseWhoseHandshakeIsRefusedOpensNoProbeSession() {
        assertThat(HandshakeRefusal.verdict(initializeBasedBase(), answered(400)))
                .isEqualTo(Verdict.NO_PROBE_SESSION);
    }

    private static HttpRequestResponse modernBase() {
        return LeakServers.modernEchoableToolCall();
    }

    private static HttpRequestResponse initializeBasedBase() {
        return LeakServers.echoableToolCall();
    }

    private static HttpRequestResponse answered(int status) {
        return HttpRequestResponse.httpRequestResponse(handshake(), refusal(status));
    }

    private static HttpResponse refusal(int status) {
        return HttpResponse.httpResponse("HTTP/1.1 " + status + " Refused\r\nContent-Length: 0\r\n\r\n");
    }

    private static HttpRequest handshake() {
        return LeakServers.echoableToolCall().request()
                .withBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
    }
}
