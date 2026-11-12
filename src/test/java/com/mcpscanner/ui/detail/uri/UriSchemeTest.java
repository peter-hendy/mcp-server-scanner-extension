package com.mcpscanner.ui.detail.uri;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class UriSchemeTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "file:///srv/app/config.yaml | file",
            "HTTPS://example.com | HTTPS",
            "repo+git://x | repo+git",
            "relative/path | ''",
            "'' | ''"})
    void readsTheScheme(String uri, String expected) {
        assertThat(UriScheme.of(uri)).isEqualTo(expected);
    }

    @Test
    void aMissingSchemeSourceHasNoScheme() {
        assertThat(UriScheme.of(null)).isEmpty();
    }
}
