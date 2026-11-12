package com.mcpscanner.checks.catalog.jwtvalidation;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSegmentsTest {

    @Test
    void encodesJsonAsUnpaddedBase64Url() {
        assertThat(JwtSegments.encode(TestTokens.object("{\"alg\":\"none\"}"))).isEqualTo("eyJhbGciOiJub25lIn0");
    }

    @Test
    void encodesBytesWithTheUrlAlphabetAndNoPadding() {
        assertThat(JwtSegments.encode(new byte[]{(byte) 0xfb, (byte) 0xff})).isEqualTo("-_8");
    }

    @Test
    void decodesAnObjectSegment() {
        assertThat(JwtSegments.decodeObject("eyJhbGciOiJub25lIn0")).hasValueSatisfying(
                node -> assertThat(node.path("alg").asText()).isEqualTo("none"));
    }

    @Test
    void decodingRejectsNonObjectsAndInvalidBase64() {
        assertThat(JwtSegments.decodeObject(JwtSegments.encode("[1]".getBytes(StandardCharsets.UTF_8)))).isEmpty();
        assertThat(JwtSegments.decodeObject("%%%")).isEmpty();
        assertThat(JwtSegments.decodeObject(JwtSegments.encode("{".getBytes(StandardCharsets.UTF_8)))).isEmpty();
    }
}
