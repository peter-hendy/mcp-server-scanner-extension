package com.mcpscanner.checks.catalog.downstreamrouteescape;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RejectionWordingTest {

    private final RejectionWording wording = new RejectionWording();

    @ParameterizedTest
    @ValueSource(strings = {
            "Invalid identifier: path separators are not allowed",
            "path cannot contain dot segments",
            "Access DENIED",
            "Forbidden",
            "illegal character in id",
            "request rejected",
            "Unauthorized",
            "Bad Request",
            "operation not permitted",
            "traversal disallowed",
            "GitLab URL path segments cannot be '.' or '..'",
            "id must not contain slashes",
            "not a valid identifier",
            "path traversal attempt",
            "prohibited value"})
    void statedRejectionsAreRecognised(String text) {
        assertThat(wording.statedIn(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"service\":\"stub-api\"}",
            "{\"marker\":\"escaped-root\",\"errors\":[]}",
            "{\"collections\":[\"invalidations\"]}",
            "{\"error\":\"not found\"}"})
    void ordinaryResultsAreNotRejections(String text) {
        assertThat(wording.statedIn(text)).isFalse();
    }
}
