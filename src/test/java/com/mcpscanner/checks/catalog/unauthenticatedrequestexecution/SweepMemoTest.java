package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.checks.shared.auth.AuthVariant;
import com.mcpscanner.checks.shared.auth.RequestMutation;
import com.mcpscanner.checks.shared.auth.VariantFamily;
import com.mcpscanner.checks.shared.probe.ProbeExchange;
import com.mcpscanner.checks.shared.probe.SimulatedMcpServer;
import com.mcpscanner.testutil.ImmutableHttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SweepMemoTest {

    private static final String ENDPOINT = "http://mcp.example/mcp";
    private static final Duration LIFETIME = Duration.ofMinutes(10);
    private static final Instant START = Instant.parse("2026-11-06T12:00:00Z");
    private static final AuthVariant FIRST = variant("first", "a");
    private static final AuthVariant SECOND = variant("second", "b");
    private static final AuthVariant THIRD = variant("third", "c");
    private static final List<AuthVariant> ALL = List.of(FIRST, SECOND, THIRD);
    private static final Map<String, AuthVariant> SESSION_VARIANTS = new ConcurrentHashMap<>();

    private Clock clock = Clock.fixed(START, ZoneOffset.UTC);
    private final SweepMemo memo = new SweepMemo(new SettledRejection(), () -> clock.instant(), LIFETIME);

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void everyVariantIsPendingForAnUnseenEndpoint() {
        assertThat(memo.pending(subject(ENDPOINT, SimulatedMcpServer.toolCall()), ALL)).containsExactly(FIRST, SECOND, THIRD);
    }

    @Test
    void aSettledRejectionIsSkippedForLaterSeedsOfTheSameEndpointAndMethod() {
        memo.observe(subject(ENDPOINT, SimulatedMcpServer.toolCall()), SECOND, rejected(), CONTROLS);

        assertThat(memo.pending(subject(ENDPOINT, SimulatedMcpServer.toolCallOf("other")), ALL))
                .containsExactly(FIRST, THIRD);
    }

    @Test
    void anAnswerThatIsNotASettledRejectionIsSweptAgain() {
        memo.observe(subject(ENDPOINT, SimulatedMcpServer.toolCall()), SECOND, answered(503), CONTROLS);

        assertThat(memo.pending(subject(ENDPOINT, SimulatedMcpServer.toolCall()), ALL)).hasSize(3);
    }

    @Test
    void aRejectionDoesNotCarryToAnotherMethodPathQueryOrOrigin() {
        memo.observe(subject(ENDPOINT, SimulatedMcpServer.toolCall()), SECOND, rejected(), CONTROLS);

        assertThat(memo.pending(subject(ENDPOINT, SimulatedMcpServer.toolsList()), ALL)).hasSize(3);
        assertThat(memo.pending(subject("http://mcp.example/other", SimulatedMcpServer.toolCall()), ALL)).hasSize(3);
        assertThat(memo.pending(subject(ENDPOINT + "?server=b", SimulatedMcpServer.toolCall()), ALL)).hasSize(3);
        assertThat(memo.pending(subject("https://mcp.example/mcp", SimulatedMcpServer.toolCall()), ALL)).hasSize(3);
    }

    @Test
    void aRejectionCarriesAcrossSessions() {
        memo.observe(subject(ENDPOINT, SimulatedMcpServer.toolCall(), "first"), SECOND, rejected(), CONTROLS);

        assertThat(memo.pending(subject(ENDPOINT, SimulatedMcpServer.toolCall(), "second"), ALL))
                .containsExactly(FIRST, THIRD);
    }

    @Test
    void aSessionVariantRejectionCarriesOnlyToTheSameSessionValue() {
        memo.observe(subject(ENDPOINT, SimulatedMcpServer.toolCall()), sessionVariant("stale"), rejected(),
                CONTROLS);

        assertThat(memo.pending(subject(ENDPOINT, SimulatedMcpServer.toolCall()),
                List.of(sessionVariant("live"), sessionVariant("stale")))).containsExactly(sessionVariant("live"));
    }

    @Test
    void aRejectionExpiresAfterTheLifetime() {
        memo.observe(subject(ENDPOINT, SimulatedMcpServer.toolCall()), SECOND, rejected(), CONTROLS);
        clock = Clock.fixed(START.plus(LIFETIME).plusSeconds(1), ZoneOffset.UTC);

        assertThat(memo.pending(subject(ENDPOINT, SimulatedMcpServer.toolCall()), ALL)).hasSize(3);
    }

    @Test
    void clearingForgetsEverySettledRejection() {
        memo.observe(subject(ENDPOINT, SimulatedMcpServer.toolCall()), FIRST, rejected(), CONTROLS);

        memo.clear();

        assertThat(memo.pending(subject(ENDPOINT, SimulatedMcpServer.toolCall()), ALL)).hasSize(3);
    }

    private static final List<ProbeExchange> CONTROLS = List.of(answered(401));

    private static ProbeExchange rejected() {
        return answered(401);
    }

    private static ProbeExchange answered(int status) {
        HttpRequest request = SimulatedMcpServer.request(SimulatedMcpServer.toolCall(), Map.of());
        return ProbeExchange.of(HttpRequestResponse.httpRequestResponse(request,
                HttpResponse.httpResponse("HTTP/1.1 " + status + " X\r\n\r\n{\"error\":\"no\"}")));
    }

    private static AuditSubject subject(String url, String body) {
        return subject(url, body, Map.of());
    }

    private static AuditSubject subject(String url, String body, String sessionId) {
        return subject(url, body, Map.of("Mcp-Session-Id", sessionId));
    }

    private static AuditSubject subject(String url, String body, Map<String, String> headers) {
        HttpRequest request = ImmutableHttpRequest.post(url, headers, body);
        HttpRequestResponse baseline = mock(HttpRequestResponse.class);
        when(baseline.request()).thenReturn(request);
        return new AuditSubject(baseline, SeedMessage.of(request).orElseThrow(), true);
    }

    private static AuthVariant sessionVariant(String sessionId) {
        return SESSION_VARIANTS.computeIfAbsent(sessionId, id -> AuthVariant.uncontrolled(VariantFamily.SESSION,
                "session.retained-id", new RequestMutation(id, request -> request)));
    }

    private static AuthVariant variant(String name, String value) {
        return AuthVariant.uncontrolled(VariantFamily.PATH, name, new RequestMutation(value, request -> request));
    }
}
