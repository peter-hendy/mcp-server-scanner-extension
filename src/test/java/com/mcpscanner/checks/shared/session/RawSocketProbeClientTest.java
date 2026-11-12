package com.mcpscanner.checks.shared.session;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.http.wire.OneConnectionServer;
import com.mcpscanner.http.wire.SocketHttpTransport;
import com.mcpscanner.testutil.ClosedPort;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RawSocketProbeClientTest {

    private static final String PING = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
    private static final String ANSWER = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}";
    private static final String EVENT_STREAM_REPLY = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n"
            + "X-Served-By: stream\r\n\r\nevent: message\ndata: " + ANSWER + "\n\n";

    private final RawSocketProbeClient client = new RawSocketProbeClient(Duration.ofMillis(500));

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void sendsAHostHeaderNamingAnotherHostVerbatim() throws Exception {
        try (OneConnectionServer server = OneConnectionServer.replyingWith(EVENT_STREAM_REPLY)) {
            client.send(ping(server.port()).withHeader("Host", "attacker.example"));

            assertThat(server.received().head()).contains("\r\nHost: attacker.example\r\n");
        }
    }

    @Test
    void liftsTheJsonRpcAnswerOutOfAnEventStreamReply() throws Exception {
        try (OneConnectionServer server = OneConnectionServer.replyingWith(EVENT_STREAM_REPLY)) {
            HttpRequestResponse exchange = client.send(ping(server.port())).orElseThrow();

            assertThat(exchange.response().statusCode()).isEqualTo((short) 200);
            assertThat(exchange.response().bodyToString()).isEqualTo(ANSWER);
            assertThat(exchange.response().headerValue("Content-Type")).isEqualTo("application/json");
            assertThat(exchange.response().headerValue("X-Served-By")).isEqualTo("stream");
        }
    }

    @Test
    void aProbeTheServerNeverAnswersWasSentWithoutAResponse() throws Exception {
        try (OneConnectionServer server = OneConnectionServer.silent()) {
            Optional<HttpRequestResponse> exchange = client.send(ping(server.port()));

            assertThat(exchange).hasValueSatisfying(sent -> assertThat(sent.response()).isNull());
        }
    }

    @Test
    void aProbeThatCouldNotConnectWasNotSent() throws IOException {
        assertThat(client.send(ping(ClosedPort.onLoopback()))).isEmpty();
    }

    @Test
    void aProbeToAnUnusableServiceWasNotSentRatherThanThrown() {
        HttpRequest unusablePort = ping(1).withService(HttpService.httpService("127.0.0.1", 70_000, false));

        assertThat(client.send(unusablePort)).isEmpty();
    }

    @Test
    void aReplyThatFailsToParseAtRuntimeWasSentWithoutAResponseRatherThanThrown() throws Exception {
        ProbeReplyReader failingReader = mock(ProbeReplyReader.class);
        when(failingReader.read(anyInt(), any(), any(), any())).thenThrow(new IllegalStateException("unparsable"));
        RawSocketProbeClient failingClient = new RawSocketProbeClient(
                new SocketHttpTransport(Duration.ofMillis(500)), failingReader);
        try (OneConnectionServer server = OneConnectionServer.replyingWith(EVENT_STREAM_REPLY)) {
            Optional<HttpRequestResponse> exchange = failingClient.send(ping(server.port()));

            assertThat(exchange).hasValueSatisfying(sent -> assertThat(sent.response()).isNull());
        }
    }

    private static HttpRequest ping(int port) {
        return HttpRequest.httpRequestFromUrl("http://127.0.0.1:" + port + "/mcp").withMethod("POST")
                .withBody(PING);
    }
}
