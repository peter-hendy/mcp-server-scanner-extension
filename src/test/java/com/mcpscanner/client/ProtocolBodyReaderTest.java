package com.mcpscanner.client;

import com.mcpscanner.mcp.ServerPingAnswerer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static com.mcpscanner.testutil.AwaitedFutures.doneWithin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtocolBodyReaderTest {

    private static final Duration DEADLINE = Duration.ofSeconds(5);

    @Test
    void readsBodiesConcurrently() {
        CyclicBarrier allReading = new CyclicBarrier(3);
        ProtocolBodyReader reader = new ProtocolBodyReader(DEADLINE, 3);

        List<CompletableFuture<String>> reads = List.of(read(reader, new MeetingBody(allReading)),
                read(reader, new MeetingBody(allReading)), read(reader, new MeetingBody(allReading)));

        assertThat(reads).allSatisfy(read -> assertThat(doneWithin(read, DEADLINE).join()).isEqualTo("{}"));
    }

    @Test
    void refusesReadsBeyondTheWorkerLimitWithoutWaiting() throws Exception {
        StuckBody stuck = new StuckBody();
        ProtocolBodyReader reader = new ProtocolBodyReader(DEADLINE, 1);
        CompletableFuture<String> holding = read(reader, stuck);
        try {
            stuck.reading.get(5, TimeUnit.SECONDS);

            assertThatThrownBy(() -> reader.read(response(new StuckBody()), "{}", ServerPingAnswerer.ignoring()))
                    .isInstanceOf(IOException.class)
                    .hasMessage("Too many MCP response body reads in progress");
        } finally {
            stuck.release.complete(null);
            doneWithin(holding, DEADLINE);
        }
    }

    private static CompletableFuture<String> read(ProtocolBodyReader reader, InputStream body) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return reader.read(response(body), "{}", ServerPingAnswerer.ignoring());
            } catch (IOException | InterruptedException failure) {
                throw new IllegalStateException(failure);
            }
        });
    }

    private static ProtocolHttpResponse response(InputStream body) {
        return new ProtocolHttpResponse(200, Map.of("Content-Type", List.of("application/json")), body,
                URI.create("http://127.0.0.1/mcp"));
    }

    private static final class MeetingBody extends InputStream {
        private final CyclicBarrier meeting;
        private final InputStream reply = new java.io.ByteArrayInputStream("{}".getBytes());
        private boolean met;

        private MeetingBody(CyclicBarrier meeting) {
            this.meeting = meeting;
        }

        @Override
        public int read() throws IOException {
            meetOnce();
            return reply.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            meetOnce();
            return reply.read(buffer, offset, length);
        }

        private void meetOnce() throws IOException {
            if (met) return;
            met = true;
            try {
                meeting.await(5, TimeUnit.SECONDS);
            } catch (Exception serialised) {
                throw new IOException("Body reads did not overlap", serialised);
            }
        }
    }

    private static final class StuckBody extends InputStream {
        private final CompletableFuture<Void> reading = new CompletableFuture<>();
        private final CompletableFuture<Void> release = new CompletableFuture<>();

        @Override
        public int read() {
            reading.complete(null);
            release.join();
            return -1;
        }
    }
}
