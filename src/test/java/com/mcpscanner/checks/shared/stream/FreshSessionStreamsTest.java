package com.mcpscanner.checks.shared.stream;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.checks.shared.probe.ProbeChannel;
import com.mcpscanner.checks.shared.session.ProbeSessions;
import com.mcpscanner.testutil.FakeMcpServer;
import com.mcpscanner.testutil.ImmutableHttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.OutsideBurpRecorder;
import com.mcpscanner.testutil.ScriptedBurpHttp;
import com.mcpscanner.testutil.SendOnlyBurpHttp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class FreshSessionStreamsTest {

    private static final int AMPLE = 100;

    private final List<HttpRequest> opened = new ArrayList<>();
    private final StreamOpener recording = request -> {
        opened.add(request);
        return StreamOpening.unanswered(request);
    };
    private final FreshSessionStreams streams =
            new FreshSessionStreams(new ProbeSessions(NoAuthStrategy::new), request -> recording.open(request));

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void opensTheStreamInAFreshProbeSessionShapedByTheCaller() {
        Http http = ScriptedBurpHttp.fakeMcpServer();

        Optional<StreamOpening> opening = streams.open(base("http://target.test/mcp"), http,
                new ProbeChannel(http, AMPLE), request -> request.withHeader("Origin", "http://evil.example"));

        assertThat(opening).isPresent();
        assertThat(opened).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.headerValue("Mcp-Session-Id")).isEqualTo(FakeMcpServer.SESSION_ID);
            assertThat(request.headerValue("Origin")).isEqualTo("http://evil.example");
        });
    }

    @Test
    void declaresTheProbeSessionsNegotiatedVersionInsteadOfTheModernSeeds() {
        Http http = ScriptedBurpHttp.fakeMcpServer();

        streams.open(base("http://target.test/mcp", "2026-07-28"), http, new ProbeChannel(http, AMPLE),
                request -> request);

        assertThat(opened).singleElement().satisfies(request ->
                assertThat(request.headerValue("MCP-Protocol-Version")).isEqualTo(FakeMcpServer.LEGACY_VERSION));
    }

    @Test
    void keepsTheSeedsVersionWhenTheServerRefusesTheHandshake() {
        ScriptedBurpHttp refusing = new ScriptedBurpHttp(request -> Optional.of(
                FakeMcpServer.exchange(request, 405, Map.of(), "")));

        streams.open(base("http://target.test/mcp", "2026-07-28"), refusing, new ProbeChannel(refusing, AMPLE),
                request -> request);

        assertThat(opened).singleElement().satisfies(request ->
                assertThat(request.headerValue("MCP-Protocol-Version")).isEqualTo("2026-07-28"));
    }

    @Test
    void reportsTheStreamGetSentOutsideBurpToTheChecksHttp() {
        OutsideBurpRecorder http = new OutsideBurpRecorder(ScriptedBurpHttp.fakeMcpServer());

        Optional<StreamOpening> opening = streams.open(base("http://target.test/mcp"), http,
                new ProbeChannel(http, AMPLE), request -> request);

        assertThat(http.reported()).containsExactly(opening.orElseThrow().exchange());
    }

    @Test
    void opensWithoutASessionWhenTheServerRefusesTheHandshake() {
        ScriptedBurpHttp refusing = new ScriptedBurpHttp(request -> Optional.of(
                FakeMcpServer.exchange(request, 405, Map.of(), "")));

        streams.open(base("http://target.test/mcp"), refusing, new ProbeChannel(refusing, AMPLE), request -> request);

        assertThat(opened).singleElement().satisfies(request ->
                assertThat(request.hasHeader("Mcp-Session-Id")).isFalse());
    }

    @Test
    void anUnansweredHandshakeIsAnUnansweredStreamWithoutAGet() {
        Http silent = http(request -> null);

        Optional<StreamOpening> opening = streams.open(base("http://target.test/mcp"), silent,
                new ProbeChannel(silent, AMPLE), request -> request);

        assertThat(opening).hasValueSatisfying(stream -> assertThat(stream.answered()).isFalse());
        assertThat(opened).isEmpty();
    }

    @Test
    void anExhaustedChannelSendsNothing() {
        List<HttpRequest> sent = new ArrayList<>();
        Http http = http(request -> {
            sent.add(request);
            return null;
        });

        Optional<StreamOpening> opening = streams.open(base("http://target.test/mcp"), http,
                new ProbeChannel(http, 0), request -> request);

        assertThat(opening).hasValueSatisfying(stream -> assertThat(stream.answered()).isFalse());
        assertThat(sent).isEmpty();
        assertThat(opened).isEmpty();
    }

    @Test
    void aRateLimitedHandshakeStopsBeforeTheGet() {
        ScriptedBurpHttp limiting = new ScriptedBurpHttp(request -> Optional.of(
                FakeMcpServer.exchange(request, 429, Map.of(), "")));
        ProbeChannel channel = new ProbeChannel(limiting, AMPLE);

        streams.open(base("http://target.test/mcp"), limiting, channel, request -> request);

        assertThat(opened).isEmpty();
        assertThat(channel.exhausted()).isTrue();
    }

    @Test
    void theStreamGetCountsAgainstTheBudget() {
        Http http = ScriptedBurpHttp.fakeMcpServer();
        ProbeChannel channel = new ProbeChannel(http, 4);

        streams.open(base("http://target.test/mcp"), http, channel, request -> request);

        assertThat(channel.exhausted()).isTrue();
    }

    @Test
    void theSessionIsDeletedWithTheInterruptClearedAndTheInterruptIsKept() {
        List<Boolean> interruptedDuringDelete = new ArrayList<>();
        ScriptedBurpHttp server = ScriptedBurpHttp.fakeMcpServer();
        Http observing = http(request -> {
            if ("DELETE".equals(request.method())) {
                interruptedDuringDelete.add(Thread.currentThread().isInterrupted());
            }
            return server.sendRequest(request, RequestOptions.requestOptions()).response();
        });
        FreshSessionStreams interrupting = new FreshSessionStreams(new ProbeSessions(NoAuthStrategy::new),
                request -> {
                    Thread.currentThread().interrupt();
                    return StreamOpening.unanswered(request);
                });

        interrupting.open(base("http://target.test/mcp"), observing, new ProbeChannel(observing, AMPLE),
                request -> request);

        assertThat(interruptedDuringDelete).containsExactly(false);
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void skipsALegacySseMessageEndpoint() {
        Http http = ScriptedBurpHttp.fakeMcpServer();

        assertThat(streams.open(base("http://target.test/messages/?session_id=LIVE"), http,
                new ProbeChannel(http, AMPLE), request -> request)).isEmpty();
        assertThat(opened).isEmpty();
    }

    private static Http http(Function<HttpRequest, burp.api.montoya.http.message.responses.HttpResponse> answer) {
        return new SendOnlyBurpHttp() {
            @Override
            public HttpRequestResponse sendRequest(HttpRequest request, RequestOptions options) {
                return HttpRequestResponse.httpRequestResponse(request, answer.apply(request));
            }
        };
    }

    private static HttpRequestResponse base(String url) {
        return base(url, FakeMcpServer.LEGACY_VERSION);
    }

    private static HttpRequestResponse base(String url, String protocolVersion) {
        HttpRequest seed = ImmutableHttpRequest.post(url, Map.of("Content-Type", "application/json",
                "MCP-Protocol-Version", protocolVersion),
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");
        return HttpRequestResponse.httpRequestResponse(seed, null);
    }
}
