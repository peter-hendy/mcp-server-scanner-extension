package com.mcpscanner.ui;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

final class PendingReconnect {

    private final BooleanSupplier stillDisconnected;
    private final Runnable connect;
    private Optional<Continuation> awaiting = Optional.empty();

    PendingReconnect(BooleanSupplier stillDisconnected, Runnable connect) {
        this.stillDisconnected = Objects.requireNonNull(stillDisconnected, "stillDisconnected");
        this.connect = Objects.requireNonNull(connect, "connect");
    }

    Runnable request() {
        Continuation continuation = new Continuation();
        awaiting = Optional.of(continuation);
        return continuation;
    }

    void discard() {
        awaiting = Optional.empty();
    }

    private final class Continuation implements Runnable {
        @Override
        public void run() {
            if (awaiting.filter(this::equals).isEmpty()) {
                return;
            }
            awaiting = Optional.empty();
            if (stillDisconnected.getAsBoolean()) {
                connect.run();
            }
        }
    }
}
