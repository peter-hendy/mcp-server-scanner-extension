package com.mcpscanner.auth.oauth.safety;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public final class BoundedHostResolver implements HostResolver, AutoCloseable {

    @FunctionalInterface
    interface Lookup {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final Duration timeout;
    private final Lookup lookup;
    private final ExecutorService executor = Executors.newCachedThreadPool(new DaemonThreadFactory());

    BoundedHostResolver(Duration timeout, Lookup lookup) {
        this.timeout = timeout;
        this.lookup = lookup;
    }

    @Override
    public List<InetAddress> resolve(String host) throws ResolutionFailedException {
        Future<InetAddress[]> future = submit(host);
        try {
            return List.of(future.get(timeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ResolutionFailedException(
                    "DNS resolution for " + host + " timed out after " + timeout.toMillis() + "ms");
        } catch (ExecutionException e) {
            return unknownHostOrFailure(host, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new ResolutionFailedException("Interrupted while resolving " + host);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private Future<InetAddress[]> submit(String host) throws ResolutionFailedException {
        try {
            return executor.submit(() -> lookup.resolve(host));
        } catch (RejectedExecutionException closed) {
            throw new ResolutionFailedException("DNS resolver is closed; cannot resolve " + host, closed);
        }
    }

    private static List<InetAddress> unknownHostOrFailure(String host, ExecutionException e)
            throws ResolutionFailedException {
        Throwable cause = e.getCause();
        if (cause instanceof UnknownHostException) {
            return List.of();
        }
        String causeName = cause != null ? cause.getClass().getSimpleName() : "ExecutionException";
        throw new ResolutionFailedException("DNS resolution for " + host + " failed: " + causeName,
                cause != null ? cause : e);
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "SuspiciousDestinationGate-dns-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
