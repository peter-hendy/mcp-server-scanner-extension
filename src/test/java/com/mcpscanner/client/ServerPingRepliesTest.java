package com.mcpscanner.client;

import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.ServerPingAnswerer;
import com.mcpscanner.proxy.SseProxyServer;
import com.mcpscanner.proxy.TestSseProxies;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;

import static com.mcpscanner.client.McpFixtureServer.MODERN_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(30)
class ServerPingRepliesTest {
    private static final String TOKEN = "ping-secret";
    private static final String STREAMABLE_VERSION = "2025-06-18";
    private static final String SSE_VERSION = "2024-11-05";
    private static final String TOOL_CALL = "{\"name\":\"echo\",\"arguments\":{}}";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(3);
    private static final int PING_FLOOD = ServerPingAnswerer.MAX_REPLIES_PER_CALL + 4;

    private McpFixtureServer server;
    private McpScannerSession session;
    private SseProxyServer proxy;

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @AfterEach
    void shutDown() {
        if (proxy != null) proxy.stop();
        if (session != null) session.disconnect();
        if (server != null) server.close();
    }

    static Stream<Arguments> transports() {
        return Stream.of(
                Arguments.of(TransportType.STREAMABLE_HTTP,
                        McpFixtureProfile.streamable(STREAMABLE_VERSION).pingingBeforeReplies(),
                        STREAMABLE_VERSION, "session-" + STREAMABLE_VERSION),
                Arguments.of(TransportType.MODERN_HTTP, McpFixtureProfile.modernOnly().pingingBeforeReplies(),
                        MODERN_VERSION, null),
                Arguments.of(TransportType.SSE, McpFixtureProfile.sseOnly(SSE_VERSION).pingingBeforeReplies(),
                        SSE_VERSION, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("transports")
    void directCatalogAnswersPingsAndReturnsReplyWhileStreamStaysOpen(TransportType transport,
                                                                      McpFixtureProfile profile, String version,
                                                                      String sessionId) throws IOException {
        connect(profile, transport, true, REQUEST_TIMEOUT);

        var tools = session.catalog("tools/list", null);

        assertThat(tools.result()).containsKey("tools");
        assertPingRepliesCarry(version, sessionId);
        assertThat(server.pingReplies()).allSatisfy(reply ->
                assertThat(reply.authorization()).isEqualTo("Bearer " + TOKEN));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("transports")
    void directToolsCallAnswersPings(TransportType transport, McpFixtureProfile profile, String version,
                                     String sessionId) throws IOException {
        connect(profile, transport, true, REQUEST_TIMEOUT);
        if (transport == TransportType.SSE) return;

        var response = session.sendHttpOperation(session.operation("tools/call", TOOL_CALL, session.nextRequestId()));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("echoed");
        assertPingRepliesCarry(version, sessionId);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("transports")
    void proxiedToolsCallAnswersPingsOnManagedAndUnmanagedPaths(TransportType transport, McpFixtureProfile profile,
                                                                String version, String sessionId) throws Exception {
        connect(profile, transport, true, REQUEST_TIMEOUT);
        startProxy(true);

        HttpResponse<String> managed = sendThroughProxy(true);
        HttpResponse<String> unmanaged = sendThroughProxy(false);

        assertThat(managed.statusCode()).isEqualTo(200);
        assertThat(managed.body()).contains("echoed");
        assertThat(unmanaged.statusCode()).isEqualTo(200);
        assertThat(unmanaged.body()).contains("echoed");
        assertThat(server.pingReplies()).hasSizeGreaterThanOrEqualTo(2);
        assertPingRepliesCarry(version, sessionId);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("transports")
    void disabledSettingSendsNoPingReplyAndTheCatalogFailsQuickly(TransportType transport, McpFixtureProfile profile,
                                                                  String version, String sessionId) throws IOException {
        connect(profile, transport, false, SHORT_TIMEOUT);
        long started = System.nanoTime();

        assertThatThrownBy(() -> session.catalog("tools/list", null)).isInstanceOf(RuntimeException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(SHORT_TIMEOUT.multipliedBy(2));
        assertThat(server.pingReplies()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("transports")
    void disabledSettingSendsNoPingReplyThroughTheProxy(TransportType transport, McpFixtureProfile profile,
                                                       String version, String sessionId) throws Exception {
        connect(profile, transport, false, SHORT_TIMEOUT);
        startProxy(false);

        assertThat(proxiedBodyWithin(SHORT_TIMEOUT)).doesNotContain("echoed");
        assertThat(server.pingReplies()).isEmpty();
    }

    @Test
    void consecutiveCallsOverOneLegacySseStreamBothAnswerPingsAndSucceed() throws Exception {
        connect(McpFixtureProfile.sseOnly(SSE_VERSION).pingingBeforeReplies(), TransportType.SSE, true, REQUEST_TIMEOUT);
        startProxy(true);

        assertThat(session.catalog("tools/list", null).result()).containsKey("tools");
        assertThat(session.catalog("tools/list", null).result()).containsKey("tools");
        assertThat(sendThroughProxy(true).body()).contains("echoed");
        assertThat(sendThroughProxy(true).body()).contains("echoed");

        assertThat(server.pingReplies()).hasSize(4);
        assertThat(server.sseStreamsOpened()).isEqualTo(2);
        assertPingRepliesCarry(SSE_VERSION, null);
    }

    @Test
    void floodOfPingsDuringOneDirectLegacySseCallGetsExactlyTheBudgetAndTheCallCompletes() throws IOException {
        connect(McpFixtureProfile.sseOnly(SSE_VERSION).pingingBeforeReplies(PING_FLOOD), TransportType.SSE, true,
                REQUEST_TIMEOUT);

        assertThat(session.catalog("tools/list", null).result()).containsKey("tools");

        assertThat(server.pingReplies()).hasSize(ServerPingAnswerer.MAX_REPLIES_PER_CALL);
    }

    @Test
    void floodOfPingsDuringEachProxiedLegacySseCallGetsItsOwnBudget() throws Exception {
        connect(McpFixtureProfile.sseOnly(SSE_VERSION).pingingBeforeReplies(PING_FLOOD), TransportType.SSE, true,
                REQUEST_TIMEOUT);
        startProxy(true);
        int afterConnect = server.pingReplies().size();

        assertThat(sendThroughProxy(true).body()).contains("echoed");
        assertThat(server.pingReplies()).hasSize(afterConnect + ServerPingAnswerer.MAX_REPLIES_PER_CALL);
        assertThat(sendThroughProxy(true).body()).contains("echoed");
        assertThat(server.pingReplies()).hasSize(afterConnect + 2 * ServerPingAnswerer.MAX_REPLIES_PER_CALL);
    }

    @Test
    void pingsOnAnIdleProxiedLegacySseStreamGetNoReplies() throws Exception {
        connect(McpFixtureProfile.sseOnly(SSE_VERSION).pingingBeforeReplies(), TransportType.SSE, true,
                REQUEST_TIMEOUT);
        startProxy(true);
        assertThat(sendThroughProxy(true).body()).contains("echoed");
        int afterCall = server.pingReplies().size();

        server.pingOpenStreams(PING_FLOOD);

        Awaitility.await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(server.pingReplies()).hasSize(afterCall));
    }

    private void assertPingRepliesCarry(String version, String sessionId) {
        assertThat(server.pingReplies()).isNotEmpty().allSatisfy(reply -> {
            assertThat(reply.body().path("id").asText()).startsWith("ping-");
            assertThat(reply.body().path("result").isObject()).isTrue();
            assertThat(reply.body().path("result").isEmpty()).isTrue();
            assertThat(reply.protocolVersion()).isEqualTo(version);
            assertThat(reply.sessionId()).isEqualTo(sessionId);
        });
        assertThat(server.pingReplies()).noneMatch(reply -> reply.body().path("id").asText().startsWith("sampling"));
    }

    private void connect(McpFixtureProfile profile, TransportType transport, boolean answerPings, Duration timeout)
            throws IOException {
        server = new McpFixtureServer(profile);
        session = new McpScannerSession(HttpClient.newHttpClient(), new McpEventLog(null), () -> true, timeout);
        session.setServerPingsEnabled(() -> answerPings);
        session.connect(new McpServerConfig(server.endpoint(), transport, new BearerTokenAuthStrategy(TOKEN)));
    }

    private void startProxy(boolean answerPings) throws IOException {
        proxy = TestSseProxies.builder(session).responseTimeout(SHORT_TIMEOUT.plus(SHORT_TIMEOUT))
                .answerServerPings(() -> answerPings).build();
        proxy.start();
    }

    private String proxiedBodyWithin(Duration timeout) throws Exception {
        try {
            return sendThroughProxy(true, timeout).body();
        } catch (HttpTimeoutException noReply) {
            return "";
        }
    }

    private HttpResponse<String> sendThroughProxy(boolean managed) throws Exception {
        return sendThroughProxy(managed, REQUEST_TIMEOUT);
    }

    private HttpResponse<String> sendThroughProxy(boolean managed, Duration timeout) throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":41,\"method\":\"tools/call\",\"params\":" + TOOL_CALL + "}";
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + proxy.port()
                        + URI.create(session.resolvedEndpoint()).getRawPath()))
                .header("Content-Type", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        Map<String, String> baseline = session.protocolHeaders();
        baseline.forEach(request::header);
        if (managed) {
            proxy.auditHeaders(baseline).forEach(request::header);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
