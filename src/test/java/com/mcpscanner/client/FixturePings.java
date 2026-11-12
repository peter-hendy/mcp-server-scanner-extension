package com.mcpscanner.client;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class FixturePings {
    static final Duration REPLY_WAIT = Duration.ofMillis(1500);
    private static final String PROGRESS_NOTIFICATION =
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\",\"params\":{\"progress\":1}}";
    private static final String SAMPLING_REQUEST =
            "{\"jsonrpc\":\"2.0\",\"id\":\"sampling-1\",\"method\":\"sampling/createMessage\",\"params\":{}}";

    record PingReply(String channel, JsonNode body, String protocolVersion, String sessionId, String authorization) {}

    interface EventSink {
        void send(String json) throws IOException;
    }

    private final AtomicInteger ids = new AtomicInteger();
    private final Map<String, CountDownLatch> pending = new ConcurrentHashMap<>();
    private final List<PingReply> replies = new CopyOnWriteArrayList<>();

    static boolean isClientReply(JsonNode body) {
        return !body.has("method") && (body.has("result") || body.has("error"));
    }

    private static String ping(String id) {
        return "{\"jsonrpc\":\"2.0\",\"id\":\"" + id + "\",\"method\":\"ping\"}";
    }

    List<PingReply> replies() {
        return List.copyOf(replies);
    }

    void accept(PingReply reply) {
        replies.add(reply);
        CountDownLatch answered = pending.get(reply.body().path("id").asText());
        if (answered != null) {
            answered.countDown();
        }
    }

    boolean pingAndAwaitReply(EventSink events, int pings) throws IOException {
        String id = "ping-" + ids.incrementAndGet();
        CountDownLatch answered = new CountDownLatch(1);
        pending.put(id, answered);
        try {
            events.send(PROGRESS_NOTIFICATION);
            events.send(SAMPLING_REQUEST);
            events.send(ping(id));
            for (int extra = 1; extra < pings; extra++) {
                events.send(ping("ping-" + ids.incrementAndGet()));
            }
            return answered.await(REPLY_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            pending.remove(id);
        }
    }
}
