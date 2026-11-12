package com.mcpscanner.client;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class HttpAuthResponsesTest {

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void bearerRejectionsAreAuthChallenges(int status) {
        assertThat(HttpAuthResponses.isAuthChallenge(status)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 202, 400, 404, 407, 500})
    void otherStatusesAreNotAuthChallenges(int status) {
        assertThat(HttpAuthResponses.isAuthChallenge(status)).isFalse();
    }
}
