package com.mcpscanner.proxy;

import com.mcpscanner.client.ProtocolHttpRequest;
import com.mcpscanner.client.ProtocolHttpResponse;
import com.mcpscanner.client.ProtocolHttpTransport;
import com.mcpscanner.testutil.FakeMcpServer;
import com.mcpscanner.testutil.ScriptedProtocolTransport;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class UpstreamCapturesTest {

    private static final URI ENDPOINT = URI.create("https://upstream.test/mcp");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final UpstreamCaptures captures = new UpstreamCaptures();
    private final ScriptedProtocolTransport upstream = new ScriptedProtocolTransport(request ->
            FakeMcpServer.exchange(request, 200, Map.of("Content-Type", List.of("application/json"),
                    "X-Upstream", List.of("yes")), "{\"echo\":" + request.body() + "}"));
    private final ProtocolHttpTransport recording = captures.recording(upstream);

    @Test
    void sendsOutsideACaptureAreNotRecorded() throws Exception {
        String body = read(recording.send(post("{\"id\":1}"), TIMEOUT));

        try (ForwardCapture capture = captures.open()) {
            assertThat(capture.chosenFor("{\"id\":1}")).isEmpty();
        }
        assertThat(body).isEqualTo("{\"echo\":{\"id\":1}}");
    }

    @Test
    void recordsTheRequestAsSentAndTheResponseBodyTheCallerRead() throws Exception {
        try (ForwardCapture capture = captures.open()) {
            String body = read(recording.send(post("{\"id\":1}"), TIMEOUT));

            RecordedExchange recorded = capture.chosenFor("{\"id\":1}").orElseThrow();
            assertThat(body).isEqualTo("{\"echo\":{\"id\":1}}");
            assertThat(recorded.uri()).isEqualTo(ENDPOINT);
            assertThat(recorded.method()).isEqualTo("POST");
            assertThat(recorded.requestHeaders()).containsEntry("Authorization", List.of("Bearer injected"));
            assertThat(recorded.requestBody()).isEqualTo("{\"id\":1}");
            assertThat(recorded.status()).isEqualTo(200);
            assertThat(recorded.responseHeaders()).containsEntry("X-Upstream", List.of("yes"));
            assertThat(new String(recorded.responseBody(), StandardCharsets.UTF_8))
                    .isEqualTo("{\"echo\":{\"id\":1}}");
        }
    }

    @Test
    void choosesTheExchangeCarryingTheForwardedIdOverHandshakeAndCleanup() throws Exception {
        try (ForwardCapture capture = captures.open()) {
            send("{\"jsonrpc\":\"2.0\",\"id\":100,\"method\":\"initialize\"}");
            send("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
            send("{\"jsonrpc\":\"2.0\",\"id\":101,\"method\":\"tools/call\"}");
            read(recording.send(new ProtocolHttpRequest(ENDPOINT, "DELETE", Map.of(), null), TIMEOUT));

            assertThat(capture.chosenFor("{\"jsonrpc\":\"2.0\",\"id\":101,\"method\":\"tools/call\"}")
                    .orElseThrow().requestBody()).contains("\"id\":101");
        }
    }

    @Test
    void choosesTheLastMatchingSendWhenTheSameIdWasSentTwice() throws Exception {
        try (ForwardCapture capture = captures.open()) {
            send("{\"id\":101,\"attempt\":1}");
            send("{\"id\":101,\"attempt\":2}");

            assertThat(capture.chosenFor("{\"id\":101}").orElseThrow().requestBody()).contains("\"attempt\":2");
        }
    }

    @Test
    void choosesTheLastSendOfTheSameMethodForANotification() throws Exception {
        try (ForwardCapture capture = captures.open()) {
            send("{\"jsonrpc\":\"2.0\",\"id\":100,\"method\":\"initialize\"}");
            send("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\",\"params\":{\"n\":1}}");
            read(recording.send(new ProtocolHttpRequest(ENDPOINT, "DELETE", Map.of(), null), TIMEOUT));

            assertThat(capture.chosenFor("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\"}")
                    .orElseThrow().requestBody()).contains("\"n\":1");
        }
    }

    @Test
    void fallsBackToTheLastSendWhenNothingMatches() throws Exception {
        try (ForwardCapture capture = captures.open()) {
            send("{\"id\":1}");
            send("{\"id\":2}");

            assertThat(capture.chosenFor("[{\"id\":3}]").orElseThrow().requestBody()).isEqualTo("{\"id\":2}");
        }
    }

    @Test
    void closingTheCaptureStopsRecordingOnThatThread() throws Exception {
        ForwardCapture closed = captures.open();
        closed.close();

        send("{\"id\":1}");

        assertThat(closed.chosenFor("{\"id\":1}")).isEmpty();
    }

    @Test
    void anotherThreadsSendsAreNotRecordedInThisCapture() throws Exception {
        try (ForwardCapture capture = captures.open()) {
            CompletableFuture.runAsync(() -> {
                try {
                    send("{\"id\":1}");
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).join();

            assertThat(capture.chosenFor("{\"id\":1}")).isEmpty();
        }
    }

    @Test
    void explicitlyRecordedExchangesJoinTheCurrentCaptureOnly() {
        RecordedExchange posted = new RecordedExchange(ENDPOINT, "POST", Map.of(), "{\"id\":5}", 202, Map.of(),
                new byte[0]);
        captures.record(posted);

        try (ForwardCapture capture = captures.open()) {
            captures.record(posted);

            assertThat(capture.chosenFor("{\"id\":5}")).contains(posted);
        }
    }

    private void send(String body) throws Exception {
        read(recording.send(post(body), TIMEOUT));
    }

    private static ProtocolHttpRequest post(String body) {
        return new ProtocolHttpRequest(ENDPOINT, "POST", Map.of("Authorization", "Bearer injected"), body);
    }

    private static String read(ProtocolHttpResponse response) throws Exception {
        try (response) {
            return new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
