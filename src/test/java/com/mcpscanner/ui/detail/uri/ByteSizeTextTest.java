package com.mcpscanner.ui.detail.uri;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.testutil.Json;
import com.mcpscanner.ui.detail.JsonValueText;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ByteSizeTextTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "12704 | 12.4 KB (12,704 bytes)",
            "0 | 0 bytes",
            "1 | 1 byte",
            "1023 | 1,023 bytes",
            "1048576 | 1.0 MB (1,048,576 bytes)",
            "5368709120 | 5.0 GB (5,368,709,120 bytes)",
            "-3 | -3",
            "1.5 | 1.5",
            "\"big\" | \"big\""})
    void describesASize(String raw, String expected) {
        assertThat(ByteSizeText.of(Json.parse(raw))).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1e400", "1e19", "9223372036854775808", "123456789012345678901234567890"})
    void sizesBeyondTheLongRangeAreShownAsGiven(String raw) {
        JsonNode size = Json.parse(raw);

        assertThat(ByteSizeText.of(size)).isEqualTo(JsonValueText.of(size));
    }
}
