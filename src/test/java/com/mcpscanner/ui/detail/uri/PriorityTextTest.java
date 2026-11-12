package com.mcpscanner.ui.detail.uri;

import com.mcpscanner.testutil.Json;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PriorityTextTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0.8 | 0.8 of 1.0",
            "0 | 0 of 1.0",
            "1 | 1 of 1.0",
            "1.5 | 1.5 (outside 0 to 1)",
            "\"high\" | \"high\""})
    void describesAPriority(String raw, String expected) {
        assertThat(PriorityText.of(Json.parse(raw))).isEqualTo(expected);
    }
}
