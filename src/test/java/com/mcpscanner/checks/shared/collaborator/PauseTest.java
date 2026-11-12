package com.mcpscanner.checks.shared.collaborator;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PauseTest {

    @Test
    void theSleepingPauseReturnsAtOnceAndKeepsTheInterruptWhenInterrupted() {
        Thread.currentThread().interrupt();
        long started = System.nanoTime();

        Pause.sleeping().pause(Duration.ofSeconds(5));

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
        assertThat(Thread.interrupted()).isTrue();
    }
}
