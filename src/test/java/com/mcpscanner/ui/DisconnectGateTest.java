package com.mcpscanner.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DisconnectGateTest {

    private final List<String> events = new ArrayList<>();
    private final DisconnectGate gate = new DisconnectGate(() -> events.add("idle"));

    @Test
    void workRequestedWhileIdleRunsAtOnce() {
        gate.whenIdle(() -> events.add("work"));

        assertThat(events).containsExactly("work");
        assertThat(gate.isDisconnecting()).isFalse();
    }

    @Test
    void workRequestedDuringADisconnectWaitsUntilItFinishes() {
        Runnable finish = gate.begin();
        gate.whenIdle(() -> events.add("work"));

        assertThat(gate.isDisconnecting()).isTrue();
        assertThat(events).isEmpty();

        finish.run();

        assertThat(gate.isDisconnecting()).isFalse();
        assertThat(events).containsExactly("idle", "work");
    }

    @Test
    void waitingWorkRunsOnlyOnce() {
        Runnable finish = gate.begin();
        gate.whenIdle(() -> events.add("work"));
        finish.run();

        gate.begin().run();

        assertThat(events).containsExactly("idle", "work", "idle");
    }
}
