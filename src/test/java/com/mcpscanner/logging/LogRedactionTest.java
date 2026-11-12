package com.mcpscanner.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogRedactionTest {

    @Test
    void redactsQueryString() {
        assertThat(LogRedaction.redactQuery("http://host/message?session_id=abc"))
                .isEqualTo("http://host/message?<redacted>");
    }

    @Test
    void leavesUrlWithoutQueryUntouched() {
        assertThat(LogRedaction.redactQuery("http://host/message")).isEqualTo("http://host/message");
    }

    @Test
    void passesNullThrough() {
        assertThat(LogRedaction.redactQuery(null)).isNull();
    }
}
