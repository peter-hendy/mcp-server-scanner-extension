package com.mcpscanner.mcp;

import burp.MessagedBurpFailureStandIn;
import burp.StreamingRefusalStandIn;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;

class BurpStreamingRefusalTest {

    @Test
    void burpsStreamingResponseRefusalIsRecognised() {
        assertThat(BurpStreamingRefusal.refused(new IllegalStateException("Streaming response received"))).isTrue();
    }

    @Test
    void scanCheckHttpsMessagelessRefusalIsRecognised() {
        assertThat(BurpStreamingRefusal.refused(new RuntimeException(new StreamingRefusalStandIn()))).isTrue();
    }

    @Test
    void otherFailuresAreNot() {
        assertThat(BurpStreamingRefusal.refused(new IllegalStateException("Connection refused"))).isFalse();
        assertThat(BurpStreamingRefusal.refused(new IllegalStateException())).isFalse();
        assertThat(BurpStreamingRefusal.refused(new RuntimeException())).isFalse();
    }

    @Test
    void wrappedFailuresOutsideBurpAreNot() {
        assertThat(BurpStreamingRefusal.refused(new RuntimeException(new IOException()))).isFalse();
        assertThat(BurpStreamingRefusal.refused(new RuntimeException(new ConnectException()))).isFalse();
    }

    @Test
    void wrappedBurpFailuresThatExplainThemselvesAreNot() {
        assertThat(BurpStreamingRefusal.refused(
                new RuntimeException(new MessagedBurpFailureStandIn("Connection timed out")))).isFalse();
    }

    @Test
    void wrappedBurpFailuresWithACauseOfTheirOwnAreNot() {
        StreamingRefusalStandIn withCause = new StreamingRefusalStandIn();
        withCause.initCause(new IOException("Connection reset"));
        assertThat(BurpStreamingRefusal.refused(new RuntimeException(withCause))).isFalse();
    }

    @Test
    void wrappedUncheckedFailuresAreNot() {
        assertThat(BurpStreamingRefusal.refused(new RuntimeException(new IllegalStateException()))).isFalse();
    }

    @Test
    void subclassedWrappersAreNot() {
        RuntimeException subclassed = new IllegalStateException(new StreamingRefusalStandIn());
        assertThat(BurpStreamingRefusal.refused(subclassed)).isFalse();
    }
}
