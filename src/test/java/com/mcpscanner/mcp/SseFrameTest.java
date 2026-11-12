package com.mcpscanner.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SseFrameTest {

    @ParameterizedTest
    @ValueSource(strings = {"event: message\ndata: {}\n\n", "event:message\ndata:{}\n\n"})
    void stripsAtMostOneOptionalSpaceAfterTheFieldColon(String frame) {
        SseFrame parsed = SseFrame.parse(frame);

        assertThat(parsed.eventType()).isEqualTo("message");
        assertThat(parsed.data()).isEqualTo("{}");
    }

    @Test
    void keepsAdditionalLeadingSpacesInData() {
        assertThat(SseFrame.parse("data:  indented\n\n").data()).isEqualTo(" indented");
    }

    @Test
    void joinsMultipleDataLinesWithLineFeeds() {
        assertThat(SseFrame.parse("data: one\ndata: two\n\n").data()).isEqualTo("one\ntwo");
    }

    @Test
    void ignoresCommentsAndUnknownFields() {
        SseFrame parsed = SseFrame.parse(": heartbeat\nid: 7\nretry: 10\n\n");

        assertThat(parsed.eventType()).isNull();
        assertThat(parsed.hasData()).isFalse();
    }

    @Test
    void lastEventFieldWins() {
        assertThat(SseFrame.parse("event: a\nevent: endpoint\ndata: /m\n\n").eventType()).isEqualTo("endpoint");
    }

    @Test
    void defaultsToTheMessageEventWhenNoEventTypeIsGiven() {
        assertThat(SseFrame.parse("data: {}\n\n").isMessage()).isTrue();
        assertThat(SseFrame.parse("event: message\ndata: {}\n\n").isMessage()).isTrue();
        assertThat(SseFrame.parse("event: endpoint\ndata: /m\n\n").isMessage()).isFalse();
    }
}
