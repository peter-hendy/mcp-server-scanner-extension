package com.mcpscanner.auth.oauth;

import fi.iki.elonen.NanoHTTPD;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CallbackListener implements AutoCloseable {

    private static final String LOOPBACK_HOST = "127.0.0.1";
    private static final int SOCKET_READ_TIMEOUT_MS = 30_000;

    private static final String SUCCESS_PAGE = "<!doctype html><html><body>"
            + "<h2>Authorization complete</h2>"
            + "<p>You can close this window now.</p>"
            + "</body></html>";

    private static final String GONE_BODY = "<!doctype html><html><body>"
            + "<h2>Already handled</h2>"
            + "<p>This authorization callback has already been processed.</p>"
            + "</body></html>";

    private final CallbackHttpServer server;
    private final CompletableFuture<CallbackResult> future = new CompletableFuture<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean callbackServed = new AtomicBoolean(false);

    private CallbackListener(int port, String path) {
        this.server = new CallbackHttpServer(port, path);
    }

    public static CallbackListener start(int port, String path) throws IOException {
        CallbackListener listener = new CallbackListener(port, path);
        listener.server.start(SOCKET_READ_TIMEOUT_MS, true);
        return listener;
    }

    public boolean isClosed() {
        return closed.get();
    }

    public int port() {
        return server.getListeningPort();
    }

    public CompletableFuture<CallbackResult> awaitCallback() {
        return future;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        server.stop();
        future.completeExceptionally(new OAuthException("Callback listener closed"));
    }

    private NanoHTTPD.Response handleCallback(Map<String, String> params) {
        if (!callbackServed.compareAndSet(false, true)) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.GONE, "text/html; charset=utf-8", GONE_BODY);
        }
        String error = params.get("error");
        CallbackResult result = new CallbackResult(
                params.get("code"), params.get("state"),
                sanitizeForLog(error),
                sanitizeForLog(params.get("error_description")), params.get("iss"));
        future.complete(result);
        scheduleStop();
        if (error == null) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK, "text/html; charset=utf-8", SUCCESS_PAGE);
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST, "text/html; charset=utf-8", "<!doctype html><html><body><h2>Authorization failed</h2>"
                        + "<p>Return to the extension for details.</p></body></html>");
    }

    private void scheduleStop() {
        Thread stopThread = new Thread(() -> {
            // Yield briefly so the response is flushed before the socket closes.
            try { Thread.sleep(50); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            close();
        }, "callback-listener-stop");
        stopThread.setDaemon(true);
        stopThread.start();
    }

    static String sanitizeForLog(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("[\r\n\t]", " ");
    }

    private final class CallbackHttpServer extends NanoHTTPD {

        private final String callbackPath;

        private CallbackHttpServer(int port, String path) {
            super(LOOPBACK_HOST, port);
            this.callbackPath = path;
        }

        @Override
        public Response serve(IHTTPSession session) {
            if (!callbackPath.equals(session.getUri())) {
                return newFixedLengthResponse(
                        Response.Status.NOT_FOUND, "text/plain; charset=utf-8", "Not found");
            }
            return handleCallback(session.getParms());
        }
    }
}
