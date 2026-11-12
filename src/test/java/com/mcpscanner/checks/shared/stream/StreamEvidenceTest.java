package com.mcpscanner.checks.shared.stream;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StreamEvidenceTest {

    private static final Map<String, List<String>> STREAM = Map.of("content-type", List.of("text/event-stream"));
    private static final byte[] WHOLE = "data: café".getBytes(StandardCharsets.UTF_8);

    @Test
    void theStatusLineCarriesTheReasonPhrase() {
        assertThat(StreamEvidence.raw(403, STREAM, new byte[0])).startsWith("HTTP/1.1 403 Forbidden\r\n");
        assertThat(StreamEvidence.raw(200, STREAM, new byte[0])).startsWith("HTTP/1.1 200 OK\r\n");
    }

    @Test
    void anUnknownStatusKeepsAnEmptyReason() {
        assertThat(StreamEvidence.raw(299, STREAM, new byte[0])).startsWith("HTTP/1.1 299 \r\n");
    }

    @Test
    void aPrefixCutInsideACharacterDropsThePartialCharacter() {
        byte[] cut = Arrays.copyOf(WHOLE, WHOLE.length - 1);

        assertThat(StreamEvidence.raw(200, STREAM, cut)).endsWith("\r\n\r\ndata: caf");
    }

    @Test
    void aWholePrefixIsKept() {
        assertThat(StreamEvidence.raw(200, STREAM, WHOLE)).endsWith("\r\n\r\ndata: café");
    }

    @Test
    void aCutFourByteCharacterIsDroppedWhole() {
        byte[] emoji = "x😀".getBytes(StandardCharsets.UTF_8);

        assertThat(StreamEvidence.raw(200, STREAM, Arrays.copyOf(emoji, 3))).endsWith("\r\n\r\nx");
    }
}
