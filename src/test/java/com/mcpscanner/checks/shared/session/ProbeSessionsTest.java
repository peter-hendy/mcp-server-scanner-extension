package com.mcpscanner.checks.shared.session;

import burp.api.montoya.http.RedirectionMode;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.client.ConnectedEndpoint;
import com.mcpscanner.client.ProtocolHttpRequest;
import com.mcpscanner.client.ProtocolHttpResponse;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.mcp.ScannerSentinels;
import com.mcpscanner.proxy.McpHttpHandler;
import com.mcpscanner.proxy.SseProxyServer;
import com.mcpscanner.testutil.FakeMcpServer;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.RecordingRequestOptions;
import com.mcpscanner.testutil.ScriptedBurpHttp;
import com.mcpscanner.testutil.SendOnlyBurpHttp;
import com.mcpscanner.core.connection.HttpProbeSession;
import com.mcpscanner.core.protocol.McpRequests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProbeSessionsTest {

    private static final String ENDPOINT = "http://target.test/mcp";
    private static final String LIVE_SSE_MESSAGES = "http://target.test/messages/?session_id=LIVE";
    private static final String REPLACEMENT_SESSION_ID = "session-b";
    private static final String BASE_SESSION_ID = "live-session";
    private static final AuthStrategy BEARER = () -> Map.of("Authorization", "Bearer probe-token");

    private final List<ProtocolHttpRequest> sent = new ArrayList<>();
    private final List<String> routedServices = new ArrayList<>();
    private final List<Optional<RedirectionMode>> redirectionModes = new ArrayList<>();
    private final List<Optional<Duration>> responseTimeouts = new ArrayList<>();
    private ProbeSessions probeSessions = new ProbeSessions(() -> BEARER);
    private boolean sessionKnown = true;
    private boolean challengeOperations;
    private boolean issueTheBaseSession;
    private boolean refuseInitialized;

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void opensAFreshSessionAtTheBaseEndpointWithConfiguredCredentials() {
        HttpProbeSession probe = open();

        assertThat(probe.sessionId()).contains(FakeMcpServer.SESSION_ID);
        ProtocolHttpRequest initialize = sent.get(0);
        assertThat(initialize.uri()).hasToString(ENDPOINT);
        assertThat(initialize.method()).isEqualTo("POST");
        assertThat(header(initialize, "Authorization")).contains("Bearer probe-token");
        assertThat(header(initialize, "Mcp-Session-Id")).isEmpty();
        assertThat(initialize.body()).contains("\"protocolVersion\":\"" + FakeMcpServer.LEGACY_VERSION + "\"");
    }

    @Test
    void terminatesWithADeleteAndReportsTheServerStatus() {
        HttpProbeSession probe = open();

        assertThat(probe.terminate().status()).isEqualTo(204);

        ProtocolHttpRequest delete = sent.get(sent.size() - 1);
        assertThat(delete.method()).isEqualTo("DELETE");
        assertThat(delete.uri()).hasToString(ENDPOINT);
        assertThat(header(delete, "Mcp-Session-Id")).contains(FakeMcpServer.SESSION_ID);
        assertThat(header(delete, "Authorization")).contains("Bearer probe-token");
    }

    @Test
    void reportsSessionNotFoundWithoutReinitializing() {
        HttpProbeSession probe = open();
        probe.terminate();

        int status = probe.send(probe.session().operation("tools/list", "{}", 2)).status();

        assertThat(status).isEqualTo(404);
        assertThat(sent).filteredOn(request -> request.body().contains("\"initialize\"")).hasSize(1);
        assertThat(header(sent.get(sent.size() - 1), "Mcp-Session-Id")).contains(FakeMcpServer.SESSION_ID);
    }

    @Test
    void appliesTheConfiguredResponseTimeoutToEveryProbeRequest() {
        probeSessions = new ProbeSessions(() -> BEARER, Duration.ofSeconds(7));

        open().terminate();

        assertThat(responseTimeouts).isNotEmpty().containsOnly(Optional.of(Duration.ofSeconds(7)));
    }

    @Test
    void neverFollowsRedirects() {
        open().terminate();

        assertThat(redirectionModes).isNotEmpty().containsOnly(Optional.of(RedirectionMode.NEVER));
    }

    @Test
    void keepsEveryAuthenticationChallengeTheServerSends() {
        HttpProbeSession probe = open();
        challengeOperations = true;

        var response = probe.send(probe.session().operation("tools/list", "{}", 2));

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.headers().entrySet()).anySatisfy(header -> {
            assertThat(header.getKey()).isEqualToIgnoringCase("WWW-Authenticate");
            assertThat(header.getValue()).contains("Basic").contains("Bearer");
        });
    }

    @Test
    void refusesToProbeALiveSseMessageEndpoint() {
        Optional<HttpProbeSession> probe = open(LIVE_SSE_MESSAGES, sseModeRouting());

        assertThat(probe).isEmpty();
        assertThat(sent).isEmpty();
    }

    @Test
    void probeRequestsOnTheLiveSsePathBypassTheProxy() {
        HttpProbeSession probe = open("http://target.test/messages/", sseModeRouting()).orElseThrow();
        probe.terminate();

        assertThat(routedServices).hasSize(sent.size()).containsOnly("target.test:80");
        assertThat(sent).allSatisfy(request ->
                assertThat(header(request, ScannerSentinels.DIRECT_DELIVERY_HEADER)).isEmpty());
    }

    @Test
    void reportsEveryProbeExchangeToTheCallersObserver() {
        List<HttpRequestResponse> observed = new ArrayList<>();
        HttpProbeSession probe = open(ENDPOINT, UnaryOperator.identity(), observed::add).orElseThrow();

        probe.send(probe.session().operation("tools/list", "{}", 2));
        probe.terminate();

        assertThat(observed).hasSize(sent.size());
        assertThat(observed).anySatisfy(exchange -> {
            assertThat(exchange.request().bodyToString()).contains("tools/list");
            assertThat(exchange.response().statusCode()).isEqualTo((short) 200);
        });
        assertThat(observed.get(observed.size() - 1).request().method()).isEqualTo("DELETE");
        assertThat(observed).allSatisfy(exchange -> assertThat(exchange.response()).isNotNull());
    }

    @Test
    void sendsAProbeSessionRequestWithoutTheConfiguredCredentials() {
        ProbeEndpoint endpoint = endpoint(ENDPOINT, UnaryOperator.identity(), exchange -> { }).orElseThrow();
        HttpProbeSession probe = endpoint.open();

        endpoint.sendWithoutCredentials(probe.session().operation("ping", "{}", 7));

        ProtocolHttpRequest anonymous = sent.get(sent.size() - 1);
        assertThat(anonymous.body()).contains("\"ping\"");
        assertThat(header(anonymous, "Mcp-Session-Id")).contains(FakeMcpServer.SESSION_ID);
        assertThat(header(anonymous, "Authorization")).isEmpty();
    }

    @Test
    void sendsARequestWithTheConfiguredCredentialsAndOnlyTheSessionItNames() {
        ProbeEndpoint endpoint = endpoint(ENDPOINT, UnaryOperator.identity(), exchange -> { }).orElseThrow();

        endpoint.send(McpRequests.httpPost("ping", FakeMcpServer.LEGACY_VERSION, "never-issued", "{}", 8));

        ProtocolHttpRequest control = sent.get(sent.size() - 1);
        assertThat(header(control, "Mcp-Session-Id")).contains("never-issued");
        assertThat(header(control, "Authorization")).contains("Bearer probe-token");
    }

    @Test
    void aFailedHandshakeThatIssuedTheBaseSessionNeverDeletesIt() {
        issueTheBaseSession = true;
        refuseInitialized = true;

        assertThat(org.assertj.core.api.Assertions.catchThrowable(this::open)).isNotNull();

        assertThat(sent).noneMatch(request -> request.method().equals("DELETE"));
    }

    @Test
    void terminatingAProbeIssuedTheBaseSessionSendsNoDelete() {
        issueTheBaseSession = true;
        HttpProbeSession probe = open();

        assertThat(probe.terminate()).isNull();

        assertThat(sent).noneMatch(request -> request.method().equals("DELETE"));
    }

    @Test
    void offersNoProbeEndpointOnALiveSseMessageEndpoint() {
        assertThat(endpoint(LIVE_SSE_MESSAGES, UnaryOperator.identity(), exchange -> { })).isEmpty();
        assertThat(sent).isEmpty();
    }

    private static UnaryOperator<HttpRequest> sseModeRouting() {
        SseProxyServer proxy = mock(SseProxyServer.class);
        when(proxy.port()).thenReturn(9999);
        McpHttpHandler handler = new McpHttpHandler(new ConnectedEndpoint() {
            public TransportType transportType() { return TransportType.SSE; }
            public String resolvedEndpoint() { return LIVE_SSE_MESSAGES; }
        }, proxy, null);
        return handler::route;
    }

    private HttpProbeSession open() {
        return open(ENDPOINT, UnaryOperator.identity()).orElseThrow();
    }

    private Optional<HttpProbeSession> open(String url, UnaryOperator<HttpRequest> routing) {
        return endpoint(url, routing, exchange -> { }).map(ProbeEndpoint::open);
    }

    private Optional<HttpProbeSession> open(String url, UnaryOperator<HttpRequest> routing,
                                            Consumer<HttpRequestResponse> observer) {
        return endpoint(url, routing, observer).map(ProbeEndpoint::open);
    }

    private Optional<ProbeEndpoint> endpoint(String url, UnaryOperator<HttpRequest> routing,
                                             Consumer<HttpRequestResponse> observer) {
        ScriptedBurpHttp scripted = new ScriptedBurpHttp(request -> Optional.of(respond(request)));
        SendOnlyBurpHttp http = new SendOnlyBurpHttp() {
            @Override
            public HttpRequestResponse sendRequest(HttpRequest request, RequestOptions options) {
                HttpRequest routed = routing.apply(request);
                routedServices.add(routed.httpService().host() + ":" + routed.httpService().port());
                redirectionModes.add(RecordingRequestOptions.recorded(options).redirectionMode());
                responseTimeouts.add(RecordingRequestOptions.recorded(options).responseTimeout());
                return scripted.sendRequest(routed, options);
            }
        };
        HttpRequest base = HttpRequest.httpRequestFromUrl(url).withMethod("POST")
                .withHeader("MCP-Protocol-Version", FakeMcpServer.LEGACY_VERSION)
                .withHeader("Mcp-Session-Id", "live-session")
                .withHeader("Authorization", "Bearer live-token");
        return probeSessions.endpoint(HttpRequestResponse.httpRequestResponse(base, null), http, observer);
    }

    private ProtocolHttpResponse respond(ProtocolHttpRequest request) {
        sent.add(request);
        if (request.method().equals("DELETE")) {
            sessionKnown = false;
            return FakeMcpServer.exchange(request, 204, Map.of(), "");
        }
        if (issueTheBaseSession && "initialize".equals(FakeMcpServer.method(request))) {
            return FakeMcpServer.exchange(request, 200, Map.of("Mcp-Session-Id", List.of(BASE_SESSION_ID)),
                    FakeMcpServer.initializeReply(request));
        }
        if (refuseInitialized && "notifications/initialized".equals(FakeMcpServer.method(request))) {
            return FakeMcpServer.exchange(request, 400, Map.of(), "rejected");
        }
        if (challengeOperations) {
            return FakeMcpServer.exchange(request, 401,
                    Map.of("WWW-Authenticate", List.of("Basic realm=\"x\"", "Bearer error=\"invalid_token\"")), "");
        }
        if (!sessionKnown && "initialize".equals(FakeMcpServer.method(request))) {
            return FakeMcpServer.exchange(request, 200, Map.of("Mcp-Session-Id", List.of(REPLACEMENT_SESSION_ID)),
                    FakeMcpServer.initializeReply(request));
        }
        if (!sessionKnown && header(request, "Mcp-Session-Id").filter(FakeMcpServer.SESSION_ID::equals).isPresent()) {
            return FakeMcpServer.exchange(request, 404, Map.of(), "Session not found");
        }
        return FakeMcpServer.respond(request);
    }

    private static Optional<String> header(ProtocolHttpRequest request, String name) {
        return request.headers().entrySet().stream()
                .filter(header -> header.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst();
    }
}
