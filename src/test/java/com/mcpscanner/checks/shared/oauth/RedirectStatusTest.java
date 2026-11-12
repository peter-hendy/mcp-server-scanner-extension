package com.mcpscanner.checks.shared.oauth;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectStatusTest {

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 303, 307, 308})
    void redirectStatusesRedirect(int status) {
        assertThat(RedirectStatus.isRedirect(status)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 300, 304, 400})
    void otherStatusesDoNotRedirect(int status) {
        assertThat(RedirectStatus.isRedirect(status)).isFalse();
    }
}
