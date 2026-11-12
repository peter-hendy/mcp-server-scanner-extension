package com.mcpscanner.ui.detail.uri;

import com.mcpscanner.testutil.Json;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class LastModifiedTextTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "\"2026-11-09T14:22:05Z\" | 2026-11-09 14:22:05 UTC",
            "\"2026-11-09T16:22:05+02:00\" | 2026-11-09 14:22:05 UTC",
            "\"2026-11-09T14:22:05.123Z\" | 2026-11-09 14:22:05 UTC",
            "\"yesterday <b>\" | yesterday <b>",
            "\"2026-11-09\" | 2026-11-09",
            "42 | 42"})
    void describesALastModifiedTime(String raw, String expected) {
        assertThat(LastModifiedText.of(Json.parse(raw))).isEqualTo(expected);
    }
}
