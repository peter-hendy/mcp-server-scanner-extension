package com.mcpscanner.client;

import com.mcpscanner.mcp.BoundedBodyReader;
import com.mcpscanner.mcp.ServerPingAnswerer;
import com.mcpscanner.mcp.SseResponseParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class ProtocolBodyReader {

    private static final int MAX_CONCURRENT_READS = 64;

    private final ThreadPoolExecutor executor;
    private final Duration timeout;

    ProtocolBodyReader(Duration timeout) {
        this(timeout, MAX_CONCURRENT_READS);
    }

    ProtocolBodyReader(Duration timeout, int maxConcurrentReads) {
        this.timeout = timeout;
        this.executor = createExecutor(maxConcurrentReads);
    }

    SseReplyReader replyReader(InputStream sseStream) {
        return new SseReplyReader(sseStream, executor, timeout);
    }

    String read(ProtocolHttpResponse response, String requestBody, ServerPingAnswerer serverRequests)
            throws IOException, InterruptedException {
        if (HttpAuthResponses.isAuthChallenge(response.status())) {
            closeQuietly(response.body());
            return "";
        }
        try (InputStream ignored = response.body()) {
            return readWithinDeadline(response, requestBody, serverRequests);
        }
    }

    static boolean isEventStream(ProtocolHttpResponse response) {
        return response.firstHeader("Content-Type")
                .map(value -> value.toLowerCase().contains("text/event-stream"))
                .orElse(false);
    }

    static void closeQuietly(InputStream body) {
        if (body == null) return;
        try {
            body.close();
        } catch (IOException ignored) {
        }
    }

    private String readWithinDeadline(ProtocolHttpResponse response, String requestBody,
                                      ServerPingAnswerer serverRequests) throws IOException, InterruptedException {
        Future<String> read = submit(() -> readBody(response, requestBody, serverRequests));
        try {
            return read.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException timedOut) {
            throw new IOException("MCP response body read timed out");
        } catch (ExecutionException failure) {
            throw causeOf(failure);
        } finally {
            read.cancel(true);
            executor.remove((Runnable) read);
        }
    }

    private Future<String> submit(Callable<String> read) throws IOException {
        try {
            return executor.submit(read);
        } catch (RejectedExecutionException saturated) {
            throw new IOException("Too many MCP response body reads in progress");
        }
    }

    private static IOException causeOf(ExecutionException failure) {
        if (failure.getCause() instanceof IOException ioFailure) return ioFailure;
        if (failure.getCause() instanceof RuntimeException runtimeFailure) throw runtimeFailure;
        return new IOException("MCP response body read failed", failure.getCause());
    }

    private static String readBody(ProtocolHttpResponse response, String requestBody,
                                   ServerPingAnswerer serverRequests) throws IOException {
        if (!isEventStream(response)) {
            return BoundedBodyReader.readUtf8(response.body());
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ResponseBoundInputStream(response.body()), StandardCharsets.UTF_8));
        String json = SseResponseParser.extractJsonRpcResponse(reader, SseResponseParser.expectedReplyId(requestBody),
                serverRequests);
        return json != null ? json : "";
    }

    private static ThreadPoolExecutor createExecutor(int maxConcurrentReads) {
        return new ThreadPoolExecutor(0, maxConcurrentReads, 30, TimeUnit.SECONDS, new SynchronousQueue<>(), task -> {
            Thread thread = new Thread(task, "McpScannerSession-body-reader");
            thread.setDaemon(true);
            return thread;
        });
    }
}
