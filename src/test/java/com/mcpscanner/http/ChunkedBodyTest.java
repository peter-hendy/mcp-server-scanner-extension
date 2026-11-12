package com.mcpscanner.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkedBodyTest {

    private static final List<String> CHUNKED = List.of("chunked");

    @Test
    void decodesSingleChunk() {
        assertThat(unframe(CHUNKED, "7\r\n{\"a\":1}\r\n0\r\n\r\n")).isEqualTo("{\"a\":1}");
    }

    @Test
    void decodesMultipleChunks() {
        assertThat(unframe(CHUNKED, "4\r\n{\"a\"\r\n3\r\n:1}\r\n0\r\n\r\n")).isEqualTo("{\"a\":1}");
    }

    @Test
    void decodesUppercaseAndLowercaseHexSizes() {
        String sixteen = "0123456789abcdef";
        String twentySix = "abcdefghijklmnopqrstuvwxyz";

        assertThat(unframe(CHUNKED, "10\r\n" + sixteen + "\r\n1A\r\n" + twentySix + "\r\n0\r\n\r\n"))
                .isEqualTo(sixteen + twentySix);
        assertThat(unframe(CHUNKED, "1a\r\n" + twentySix + "\r\n0\r\n\r\n")).isEqualTo(twentySix);
    }

    @Test
    void ignoresChunkExtensions() {
        assertThat(unframe(CHUNKED, "5;name=value\r\nhello\r\n5 ; a=\"b\"\r\nworld\r\n0;last\r\n\r\n"))
                .isEqualTo("helloworld");
    }

    @Test
    void dropsTrailers() {
        assertThat(unframe(CHUNKED, "5\r\nhello\r\n0\r\nExpires: never\r\nX-Checksum: abc\r\n\r\n"))
                .isEqualTo("hello");
    }

    @Test
    void decodesMultiByteUtf8DataByByteCount() {
        String text = "café 🚀";
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        byte[] framed = concat(ascii(Integer.toHexString(data.length) + "\r\n"), data, ascii("\r\n0\r\n\r\n"));

        assertThat(new String(ChunkedBody.unframe(CHUNKED, framed), StandardCharsets.UTF_8)).isEqualTo(text);
    }

    @Test
    void decodesWhenChunkedIsTheLastOfSeveralCodingsInAnyCase() {
        assertThat(unframe(List.of("gzip, Chunked"), "5\r\nhello\r\n0\r\n\r\n")).isEqualTo("hello");
        assertThat(unframe(List.of("gzip", "CHUNKED"), "5\r\nhello\r\n0\r\n\r\n")).isEqualTo("hello");
    }

    @Test
    void leavesAlreadyDecodedJsonUnchanged() {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}";

        assertThat(unframe(CHUNKED, json)).isEqualTo(json);
    }

    @Test
    void leavesAlreadyDecodedSseUnchanged() {
        String sse = "event: message\r\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}\r\n\r\n";

        assertThat(unframe(CHUNKED, sse)).isEqualTo(sse);
    }

    @Test
    void leavesDecodedBodyThatStartsWithHexDigitsUnchanged() {
        String body = "abc\r\ndef";

        assertThat(unframe(CHUNKED, body)).isEqualTo(body);
    }

    @Test
    void leavesFramingWithoutTerminatingChunkUnchanged() {
        String truncated = "5\r\nhello\r\n";

        assertThat(unframe(CHUNKED, truncated)).isEqualTo(truncated);
    }

    @Test
    void leavesFramingWithShortChunkDataUnchanged() {
        String truncated = "a\r\nhello";

        assertThat(unframe(CHUNKED, truncated)).isEqualTo(truncated);
    }

    @Test
    void leavesFramingMissingFinalCrlfUnchanged() {
        String truncated = "5\r\nhello\r\n0\r\n";

        assertThat(unframe(CHUNKED, truncated)).isEqualTo(truncated);
    }

    @Test
    void leavesFramingWithTrailingBytesAfterTerminatorUnchanged() {
        String extra = "5\r\nhello\r\n0\r\n\r\nmore";

        assertThat(unframe(CHUNKED, extra)).isEqualTo(extra);
    }

    @Test
    void leavesChunkSizeTooLargeToParseUnchanged() {
        String huge = "fffffffffffffffffff\r\nhello\r\n0\r\n\r\n";

        assertThat(unframe(CHUNKED, huge)).isEqualTo(huge);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ffffffff", "80000000", "7fffffff"})
    void leavesChunkSizeBeyondTheBodyUnchanged(String sizeLine) {
        String oversized = sizeLine + "\r\nhello\r\n0\r\n\r\n";

        assertThat(unframe(CHUNKED, oversized)).isEqualTo(oversized);
    }

    @Test
    void leavesEmptyBodyUnchanged() {
        assertThat(unframe(CHUNKED, "")).isEmpty();
    }

    @Test
    void leavesFramedBodyUnchangedWhenHeaderAbsent() {
        String framed = "5\r\nhello\r\n0\r\n\r\n";

        assertThat(unframe(List.of(), framed)).isEqualTo(framed);
    }

    @Test
    void leavesFramedBodyUnchangedWhenAnotherCodingIsDeclared() {
        String framed = "5\r\nhello\r\n0\r\n\r\n";

        assertThat(unframe(List.of("gzip"), framed)).isEqualTo(framed);
    }

    private static String unframe(List<String> transferEncodings, String body) {
        return new String(ChunkedBody.unframe(transferEncodings, ascii(body)), StandardCharsets.UTF_8);
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concat(byte[]... parts) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
