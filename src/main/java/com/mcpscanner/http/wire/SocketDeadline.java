package com.mcpscanner.http.wire;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class SocketDeadline {

    private final AtomicBoolean settled = new AtomicBoolean();

    private SocketDeadline() {
    }

    static SocketDeadline closing(Socket socket, Duration after) {
        SocketDeadline deadline = new SocketDeadline();
        CompletableFuture.delayedExecutor(after.toMillis(), TimeUnit.MILLISECONDS)
                .execute(() -> deadline.expire(socket));
        return deadline;
    }

    boolean disarm() {
        return settled.compareAndSet(false, true);
    }

    private void expire(Socket socket) {
        if (settled.compareAndSet(false, true)) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
