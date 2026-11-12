package com.mcpscanner.ui.connect;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class ConnectionFailureHandler {
    private final AtomicReference<Object> currentConnection = new AtomicReference<>();
    private final Consumer<Runnable> dispatcher;
    private final Runnable failureAction;

    public ConnectionFailureHandler(Consumer<Runnable> dispatcher, Runnable failureAction) {
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.failureAction = Objects.requireNonNull(failureAction);
    }

    public Runnable beginConnection() {
        Object connection = new Object();
        currentConnection.set(connection);
        return () -> handleFailure(connection);
    }

    public void retireConnection() {
        currentConnection.set(null);
    }

    private void handleFailure(Object connection) {
        if (currentConnection.get() != connection) return;
        dispatcher.accept(() -> {
            if (currentConnection.get() == connection) failureAction.run();
        });
    }
}
