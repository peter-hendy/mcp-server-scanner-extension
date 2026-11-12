package com.mcpscanner.http.wire;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class ChunkedInputStreamTest {

    @Test
    void aChunksDataIsReturnedBeforeItsTrailingLineBreakArrives() throws IOException {
        PipedOutputStream sender = new PipedOutputStream();
        InputStream chunked = new ChunkedInputStream(new PipedInputStream(sender));
        sender.write("10\r\nevent: endpoint\n".getBytes(StandardCharsets.US_ASCII));
        sender.flush();

        byte[] buffer = new byte[64];
        int read = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> chunked.read(buffer, 0, buffer.length));

        assertThat(new String(buffer, 0, read, StandardCharsets.US_ASCII)).isEqualTo("event: endpoint\n");
    }

    @Test
    void consecutiveChunksAndTheLastChunkStillDecode() throws IOException {
        InputStream chunked = new ChunkedInputStream(new ByteArrayInputStream(
                "3\r\nabc\r\n2\r\nde\r\n0\r\n\r\n".getBytes(StandardCharsets.US_ASCII)));

        assertThat(new String(chunked.readAllBytes(), StandardCharsets.US_ASCII)).isEqualTo("abcde");
    }

    @Test
    void refusesTrailersThatNeverEnd() {
        InputStream chunked = new ChunkedInputStream(
                EndlessInputStream.repeating("0\r\n", "X-Trailer: " + "a".repeat(60_000) + "\r\n"));

        assertThatThrownBy(() -> assertTimeoutPreemptively(Duration.ofSeconds(5), chunked::readAllBytes))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("header section");
    }

    @Test
    void refusesANegativeChunkSizeAsMalformed() {
        InputStream chunked = new ChunkedInputStream(
                new ByteArrayInputStream("-1\r\nabc\r\n0\r\n\r\n".getBytes(StandardCharsets.US_ASCII)));

        assertThatThrownBy(chunked::readAllBytes)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("malformed chunk size");
    }

    @Test
    void refusesAChunkSizeBeyondALongAsMalformed() {
        InputStream chunked = new ChunkedInputStream(
                new ByteArrayInputStream("10000000000000000\r\n".getBytes(StandardCharsets.US_ASCII)));

        assertThatThrownBy(chunked::readAllBytes)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("malformed chunk size");
    }

    @Test
    void closingItClosesTheConnectionUnderneath() throws IOException {
        AtomicBoolean closed = new AtomicBoolean();
        InputStream connection = new FilterInputStream(InputStream.nullInputStream()) {
            @Override
            public void close() {
                closed.set(true);
            }
        };

        new ChunkedInputStream(connection).close();

        assertThat(closed).isTrue();
    }
}
