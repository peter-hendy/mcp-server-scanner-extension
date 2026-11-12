package com.mcpscanner.proxy;

import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.SseResponseParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Predicate;

final class SseStreamReader {

    private final String threadName;
    private final SseReplyDispatcher replies;
    private final McpEventLog eventLog;
    private final SessionLifecycle lifecycle;
    private final Predicate<String> serverRequests;
    private InputStream stream;
    private BufferedReader reader;
    private Thread thread;
    private volatile boolean running;

    SseStreamReader(String threadName, SseReplyDispatcher replies, McpEventLog eventLog, SessionLifecycle lifecycle,
                    Predicate<String> serverRequests) {
        this.threadName = threadName;
        this.replies = replies;
        this.eventLog = eventLog;
        this.lifecycle = lifecycle;
        this.serverRequests = serverRequests;
    }

    synchronized BufferedReader adopt(InputStream body) throws IOException {
        if (!lifecycle.isOpen()) {
            body.close();
            throw new IOException("Session closed");
        }
        stream = body;
        reader = new BufferedReader(new InputStreamReader(body));
        return reader;
    }

    synchronized void start() throws IOException {
        if (!lifecycle.isOpen()) {
            throw new IOException("Session closed");
        }
        replies.reopen();
        running = true;
        BufferedReader source = reader;
        thread = new Thread(() -> readLoop(source), threadName);
        thread.setDaemon(true);
        thread.start();
    }

    boolean isRunning() {
        return running;
    }

    void markStopped() {
        running = false;
    }

    synchronized void halt() {
        running = false;
        closeStream();
    }

    synchronized Thread stop() {
        halt();
        Thread stopped = thread;
        if (stopped != null) {
            stopped.interrupt();
        }
        thread = null;
        reader = null;
        return stopped;
    }

    private void readLoop(BufferedReader source) {
        try {
            while (running) {
                SseResponseParser.SseEvent event = SseResponseParser.readNextEvent(source);
                if (event == null) {
                    exit(new IOException("SSE stream closed"));
                    return;
                }
                if ("message".equals(event.eventType()) && !serverRequests.test(event.data())) {
                    replies.dispatch(event.data());
                }
            }
        } catch (Throwable e) {
            exit(e);
        }
    }

    private void exit(Throwable cause) {
        boolean requested = !running;
        running = false;
        int failed = replies.terminate(cause);
        String message = "SSE reader thread exited: " + cause.getMessage() + " (failed-waiters=" + failed + ")";
        if (requested && failed == 0) {
            eventLog.debug(message);
        } else {
            eventLog.warn(message);
        }
    }

    private void closeStream() {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
            stream = null;
        }
    }
}
