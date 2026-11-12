package com.mcpscanner.mcp;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static com.mcpscanner.testutil.AwaitedFutures.valueWithin;
import static org.assertj.core.api.Assertions.assertThat;

class StreamingResponseReaderTest {

    private static final StreamingResponseReader READER = new StreamingResponseReader(Duration.ofSeconds(30));
    private static final String REQUEST = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";

    @Test
    void convertsStreamingSseResponseToJson() throws IOException {
        String sseBody = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}\n\n";
        InputStream stream = new ByteArrayInputStream(sseBody.getBytes());

        UnstreamedReply result = READER.read(200, "text/event-stream", stream, REQUEST, ServerPingAnswerer.ignoring());

        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.contentType()).isEqualTo("application/json");
        assertThat(result.body()).isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}");
    }

    @Test
    void returnsJsonResponseUnchangedFromStream() throws IOException {
        String jsonBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}";
        InputStream stream = new ByteArrayInputStream(jsonBody.getBytes());

        UnstreamedReply result = READER.read(200, "application/json", stream, REQUEST, ServerPingAnswerer.ignoring());

        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.contentType()).isEqualTo("application/json");
        assertThat(result.body()).isEqualTo(jsonBody);
    }

    @Test
    void returnsEmptyBodyWhenSseParsingYieldsNull() throws IOException {
        String sseBody = "event: endpoint\ndata: /message\n\n";
        InputStream stream = new ByteArrayInputStream(sseBody.getBytes());

        UnstreamedReply result = READER.read(200, "text/event-stream", stream, REQUEST, ServerPingAnswerer.ignoring());

        assertThat(result.body()).isEmpty();
    }

    @Test
    void sseStreamingResponseReturnsAsSoonAsCompleteEventArrivesWithoutWaitingForEof() throws Exception {
        String completedEvent = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}\n\n";
        PipedOutputStream upstream = new PipedOutputStream();
        PipedInputStream stream = new PipedInputStream(upstream, 64 * 1024);
        upstream.write(completedEvent.getBytes(StandardCharsets.UTF_8));
        upstream.flush();
        // Deliberately do not close upstream — readAllBytes() would block forever here.

        CompletableFuture<UnstreamedReply> reading = CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return READER.read(200, "text/event-stream", stream, REQUEST, ServerPingAnswerer.ignoring());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        UnstreamedReply result = valueWithin(Duration.ofSeconds(5), reading);

        assertThat(result.contentType()).isEqualTo("application/json");
        assertThat(result.body()).isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}");
        upstream.close();
    }

    @Test
    void sseEventExceedingCapFailsWithBoundedProxyError() {
        StringBuilder huge = new StringBuilder("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"big\":\"");
        huge.append("a".repeat(2 * 1024 * 1024));
        huge.append("\"}\n\n");
        InputStream stream = new ByteArrayInputStream(huge.toString().getBytes(StandardCharsets.UTF_8));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> READER.read(200, "text/event-stream", stream, REQUEST, ServerPingAnswerer.ignoring()))
                .isInstanceOf(IOException.class);
    }

    @Test
    void nonSseResponseExceedingCapIsRejected() {
        byte[] huge = new byte[9 * 1024 * 1024];
        java.util.Arrays.fill(huge, (byte) 'x');
        InputStream stream = new ByteArrayInputStream(huge);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> READER.read(200, "application/json", stream, REQUEST, ServerPingAnswerer.ignoring()))
                .isInstanceOf(IOException.class);
    }
}
