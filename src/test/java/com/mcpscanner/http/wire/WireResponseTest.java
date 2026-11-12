package com.mcpscanner.http.wire;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class WireResponseTest {

    @Test
    void decodesAChunkedBodyAndStopsAtTheLastChunkOfAKeptAliveConnection() throws IOException {
        InputStream wire = stream("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: keep-alive\r\n\r\n"
                + "4;ext=1\r\nabcd\r\n2\r\nef\r\n0\r\nTrailer: x\r\n\r\nNEXT-RESPONSE-NEVER-READ");

        WireResponse response = WireResponse.read(wire, "POST");

        assertThat(response.status()).isEqualTo(200);
        assertThat(body(response)).isEqualTo("abcdef");
    }

    @Test
    void readsExactlyContentLengthBytes() throws IOException {
        WireResponse response = WireResponse.read(
                stream("HTTP/1.1 201 Created\r\nContent-Length: 3\r\nX-A: 1\r\nx-a: 2\r\n\r\nabcdef"), "POST");

        assertThat(body(response)).isEqualTo("abc");
        assertThat(response.headers()).extracting(WireHeader::name, WireHeader::value)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Content-Length", "3"),
                        org.assertj.core.groups.Tuple.tuple("X-A", "1"),
                        org.assertj.core.groups.Tuple.tuple("x-a", "2"));
        assertThat(response.headerValue("content-length")).contains("3");
    }

    @Test
    void readsUntilCloseWhenTheBodyIsUnframed() throws IOException {
        WireResponse response = WireResponse.read(stream("HTTP/1.0 200 OK\r\n\r\nall of it"), "POST");

        assertThat(body(response)).isEqualTo("all of it");
    }

    @Test
    void skipsInterimResponsesAndHasNoBodyForHeadOrNoContent() throws IOException {
        WireResponse afterContinue = WireResponse.read(
                stream("HTTP/1.1 100 Continue\r\n\r\nHTTP/1.1 204 No Content\r\n\r\nignored"), "POST");
        WireResponse head = WireResponse.read(stream("HTTP/1.1 200 OK\r\nContent-Length: 9\r\n\r\n"), "HEAD");

        assertThat(afterContinue.status()).isEqualTo(204);
        assertThat(body(afterContinue)).isEmpty();
        assertThat(body(head)).isEmpty();
    }

    @Test
    void refusesAMalformedContentLengthWithAClearReason() {
        assertThatThrownBy(() -> WireResponse.read(stream("HTTP/1.1 200 OK\r\nContent-Length: 2, 2\r\n\r\n{}"), "POST"))
                .isInstanceOf(MalformedResponseException.class)
                .hasMessageContaining("malformed Content-Length: 2, 2");
    }

    @Test
    void refusesAConnectionClosedBeforeAnyStatusLine() {
        assertThatThrownBy(() -> WireResponse.read(stream(""), "POST"))
                .isInstanceOf(java.io.EOFException.class);
    }

    @Test
    void refusesAHeaderSectionThatNeverEnds() {
        InputStream endlessHeaders = EndlessInputStream.repeating("HTTP/1.1 200 OK\r\n", "X-Pad: " + "a".repeat(60_000) + "\r\n");

        assertThatThrownBy(() -> assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> WireResponse.read(endlessHeaders, "POST")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("header section");
    }

    @Test
    void refusesInterimResponsesThatNeverEnd() {
        InputStream endlessContinues = EndlessInputStream.repeating("", "HTTP/1.1 100 Continue\r\n\r\n");

        assertThatThrownBy(() -> assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> WireResponse.read(endlessContinues, "POST")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("interim responses");
    }

    private static InputStream stream(String raw) {
        return new ByteArrayInputStream(raw.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String body(WireResponse response) throws IOException {
        return new String(response.body().readAllBytes(), StandardCharsets.ISO_8859_1);
    }
}
