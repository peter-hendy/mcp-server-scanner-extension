package com.mcpscanner.cvetargets.burp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class KeepAliveOrigin implements AutoCloseable {

    static final Duration DELAYED_REPLY = Duration.ofMillis(25);

    private static final String IMMEDIATE_PATH = "/immediate";
    private static final String DELAYED_PATH = "/delayed";
    private static final byte[] BODY = "ok".getBytes(StandardCharsets.US_ASCII);

    private final HttpServer listener;
    private final ExecutorService handlers = Executors.newCachedThreadPool();
    private final ScheduledExecutorService delayedReplies = Executors.newScheduledThreadPool(4);

    private KeepAliveOrigin(HttpServer listener) {
        this.listener = listener;
        listener.setExecutor(handlers);
        listener.createContext(IMMEDIATE_PATH, KeepAliveOrigin::reply);
        listener.createContext(DELAYED_PATH, exchange -> delayedReplies.schedule(() -> reply(exchange),
                DELAYED_REPLY.toMillis(), TimeUnit.MILLISECONDS));
    }

    static KeepAliveOrigin start() {
        try {
            KeepAliveOrigin origin = new KeepAliveOrigin(
                    HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0));
            origin.listener.start();
            return origin;
        } catch (IOException bindFailure) {
            throw new UncheckedIOException(bindFailure);
        }
    }

    String immediateUrl() {
        return url(IMMEDIATE_PATH);
    }

    String delayedUrl() {
        return url(DELAYED_PATH);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + listener.getAddress().getPort() + path;
    }

    private static void reply(HttpExchange exchange) {
        try (exchange) {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, BODY.length);
            exchange.getResponseBody().write(BODY);
        } catch (IOException clientGone) {
            return;
        }
    }

    @Override
    public void close() {
        listener.stop(0);
        delayedReplies.shutdownNow();
        handlers.shutdownNow();
    }
}
