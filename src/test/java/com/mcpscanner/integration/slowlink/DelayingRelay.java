package com.mcpscanner.integration.slowlink;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class DelayingRelay implements AutoCloseable {

    private final ServerSocket listener;
    private final AtomicReference<InetSocketAddress> target = new AtomicReference<>();
    private final Duration oneWayDelay;
    private final ExecutorService threads = Executors.newCachedThreadPool(DelayingRelay::daemon);
    private final AtomicInteger requestsSeen = new AtomicInteger();
    private final AtomicReference<IllegalStateException> framingFailure = new AtomicReference<>();

    private DelayingRelay(ServerSocket listener, Duration oneWayDelay) {
        this.listener = listener;
        this.oneWayDelay = oneWayDelay;
    }

    public static DelayingRelay listening(Duration oneWayDelay) throws IOException {
        return new DelayingRelay(new ServerSocket(0, 128, InetAddress.getLoopbackAddress()), oneWayDelay);
    }

    public void forwardTo(InetSocketAddress destination) {
        if (!target.compareAndSet(null, destination)) {
            throw new IllegalStateException("the relay already forwards to " + target.get());
        }
        threads.execute(this::acceptUntilClosed);
    }

    public int port() {
        return listener.getLocalPort();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + port();
    }

    public Duration roundTrip() {
        return oneWayDelay.multipliedBy(2);
    }

    public int requestsSeen() {
        Optional.ofNullable(framingFailure.get()).ifPresent(failure -> {
            throw failure;
        });
        return requestsSeen.get();
    }

    @Override
    public void close() throws IOException {
        listener.close();
        threads.shutdownNow();
    }

    private void acceptUntilClosed() {
        while (!listener.isClosed()) {
            try {
                Socket client = listener.accept();
                threads.execute(() -> relay(client));
            } catch (IOException closed) {
                return;
            }
        }
    }

    private void relay(Socket client) {
        try {
            InetSocketAddress destination = target.get();
            pipe(client, new Socket(destination.getAddress(), destination.getPort()));
        } catch (IOException unreachable) {
            Sockets.closeQuietly(client);
        }
    }

    private void pipe(Socket client, Socket upstream) throws IOException {
        client.setTcpNoDelay(true);
        upstream.setTcpNoDelay(true);
        CompletableFuture<Void> requests =
                new DelayedPipe(client, upstream, oneWayDelay, countingRequests()).start(threads);
        CompletableFuture<Void> responses =
                new DelayedPipe(upstream, client, oneWayDelay, ChunkObserver.NONE).start(threads);
        CompletableFuture.allOf(requests, responses)
                .whenComplete((ignored, failure) -> Sockets.closeQuietly(client, upstream));
    }

    private ChunkObserver countingRequests() {
        HttpRequestCounter counter = new HttpRequestCounter(requestsSeen::incrementAndGet);
        return (chunk, length) -> {
            try {
                counter.observe(chunk, length);
            } catch (IllegalStateException unframed) {
                framingFailure.compareAndSet(null, unframed);
                throw unframed;
            }
        };
    }

    private static Thread daemon(Runnable runnable) {
        Thread thread = new Thread(runnable, "delaying-relay");
        thread.setDaemon(true);
        return thread;
    }
}
