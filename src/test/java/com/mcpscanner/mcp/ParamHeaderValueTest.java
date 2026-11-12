package com.mcpscanner.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class ParamHeaderValueTest {

    @ParameterizedTest
    @ValueSource(strings = {"acme", "a b", "tab\tinside", "~!@#$%^&*()", "=?base64?abc", "abc?="})
    void sendsPrintableAsciiAsIs(String value) {
        assertThat(ParamHeaderValue.encoded(value)).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " leading", "trailing ", "\tleading", "trailing\t", "café", "line\nbreak",
            "\u0000", "\u007f", "=?base64?YWJj?="})
    void sendsEverythingElseBase64Wrapped(String value) {
        assertThat(ParamHeaderValue.encoded(value)).isEqualTo(
                "=?base64?" + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)) + "?=");
    }

    @Test
    void encodesNonAsciiAsUtf8() {
        assertThat(ParamHeaderValue.encoded("日本")).isEqualTo("=?base64?5pel5pys?=");
    }
}
