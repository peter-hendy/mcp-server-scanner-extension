package com.mcpscanner.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ByteSizeTextTest {

    private final ByteSizeText text = new ByteSizeText();

    @ParameterizedTest
    @CsvSource({"33554432, 32 MB", "268435456, 256 MB", "2048, 2 KB", "1048577, 1048577 bytes",
            "1, 1 byte", "2147483648, 2 GB"})
    void showsTheLargestUnitThatHoldsTheValueExactly(long bytes, String shown) {
        assertThat(text.format(bytes)).isEqualTo(shown);
    }

    @ParameterizedTest
    @CsvSource({"32 MB, 33554432", "32mb, 33554432", "2 KB, 2048", "1 GB, 1073741824", "2048, 2048",
            "2048 bytes, 2048", "1 byte, 1", "7 B, 7"})
    void readsASizeWithOrWithoutAUnit(String typed, long bytes) {
        assertThat(text.parse(typed)).hasValue(bytes);
    }

    @ParameterizedTest
    @CsvSource({"32 TB", "MB", "1.5 MB", "-1 MB", "99999999999999999999 GB"})
    void rejectsAnythingElse(String typed) {
        assertThat(text.parse(typed)).isEmpty();
    }

    @Test
    void roundTripsWhatItShows() {
        assertThat(text.parse(text.format(1_048_577L))).hasValue(1_048_577L);
    }
}
