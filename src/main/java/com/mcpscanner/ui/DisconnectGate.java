package com.mcpscanner.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class DisconnectGate {

    private final Runnable onIdle;
    private final List<Runnable> waiting = new ArrayList<>();
    private boolean disconnecting;

    DisconnectGate(Runnable onIdle) {
        this.onIdle = Objects.requireNonNull(onIdle, "onIdle");
    }

    Runnable begin() {
        disconnecting = true;
        return this::finish;
    }

    boolean isDisconnecting() {
        return disconnecting;
    }

    void whenIdle(Runnable next) {
        if (disconnecting) {
            waiting.add(next);
        } else {
            next.run();
        }
    }

    private void finish() {
        disconnecting = false;
        onIdle.run();
        List<Runnable> ready = List.copyOf(waiting);
        waiting.clear();
        ready.forEach(Runnable::run);
    }
}
