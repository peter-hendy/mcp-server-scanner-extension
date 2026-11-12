package com.mcpscanner.checks.catalog.crossclientresponseleak;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.IntStream;

final class ConcurrentRound {

    private static final Duration GRACE_AFTER_RESPONSE_TIMEOUT = Duration.ofSeconds(2);

    private final Supplier<String> markers;
    private final Duration roundDeadline;

    ConcurrentRound(Supplier<String> markers, Duration responseTimeout) {
        this.markers = markers;
        this.roundDeadline = responseTimeout.plus(GRACE_AFTER_RESPONSE_TIMEOUT);
    }

    List<ClientReply> run(List<ProbeClient> clients, EchoableCall call, String argument, RequestIds ids) {
        List<String> roundMarkers = IntStream.range(0, clients.size()).mapToObj(index -> markers.get()).toList();
        ExecutorService pool = Executors.newFixedThreadPool(clients.size(), ConcurrentRound::daemon);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ClientReply>> replies = IntStream.range(0, clients.size())
                    .mapToObj(index -> pool.submit(() -> {
                        start.await();
                        return clients.get(index).call(call, argument, roundMarkers.get(index), ids.idFor(index));
                    }))
                    .toList();
            start.countDown();
            long deadline = System.nanoTime() + roundDeadline.toNanos();
            return IntStream.range(0, replies.size())
                    .mapToObj(index -> outcome(replies.get(index), roundMarkers.get(index), deadline))
                    .toList();
        } finally {
            pool.shutdownNow();
        }
    }

    private static Thread daemon(Runnable runnable) {
        Thread thread = new Thread(runnable, "mcp-cross-client-probe");
        thread.setDaemon(true);
        return thread;
    }

    private static ClientReply outcome(Future<ClientReply> reply, String marker, long deadline) {
        try {
            return reply.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return ClientReply.unanswered(marker);
        } catch (ExecutionException | TimeoutException unanswered) {
            return ClientReply.unanswered(marker);
        }
    }
}
