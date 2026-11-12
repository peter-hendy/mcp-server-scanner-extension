package com.mcpscanner.integration.slowlink;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutorService;

final class DelayedPipe {

    private static final int CHUNK_SIZE = 16 * 1024;

    private final Socket source;
    private final Socket destination;
    private final long delayNanos;
    private final ChunkObserver observer;
    private final DelayQueue<DelayedChunk> inFlight = new DelayQueue<>();
    private long sequence;

    DelayedPipe(Socket source, Socket destination, Duration delay, ChunkObserver observer) {
        this.source = source;
        this.destination = destination;
        this.delayNanos = delay.toNanos();
        this.observer = observer;
    }

    CompletableFuture<Void> start(ExecutorService threads) {
        CompletableFuture<Void> read = CompletableFuture.runAsync(this::readUntilEndOfStream, threads);
        CompletableFuture<Void> delivered = CompletableFuture.runAsync(this::deliverUntilEndOfStream, threads);
        return CompletableFuture.allOf(read, delivered);
    }

    private void readUntilEndOfStream() {
        byte[] buffer = new byte[CHUNK_SIZE];
        try {
            InputStream in = source.getInputStream();
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                observer.observe(buffer, read);
                schedule(Arrays.copyOf(buffer, read));
            }
        } catch (IOException | RuntimeException closed) {
            Sockets.closeQuietly(destination);
        } finally {
            schedule(new byte[0]);
        }
    }

    private void schedule(byte[] bytes) {
        inFlight.put(new DelayedChunk(System.nanoTime() + delayNanos, sequence++, bytes));
    }

    private void deliverUntilEndOfStream() {
        try {
            OutputStream out = destination.getOutputStream();
            for (DelayedChunk chunk = inFlight.take(); !chunk.endOfStream(); chunk = inFlight.take()) {
                out.write(chunk.bytes());
                out.flush();
            }
            destination.shutdownOutput();
        } catch (IOException closed) {
            Sockets.closeQuietly(source);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
