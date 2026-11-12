package com.mcpscanner.checks.catalog.sessionlifecycle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

final class StatefulStreamableServer implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SESSION_HEADER = "Mcp-Session-Id";
    private static final Duration KEEPALIVE = Duration.ofMillis(100);
    private static final Duration MAX_STREAM = Duration.ofSeconds(15);

    private final Set<String> issued = ConcurrentHashMap.newKeySet();
    private final Set<String> open = ConcurrentHashMap.newKeySet();
    private final Set<String> deleted = ConcurrentHashMap.newKeySet();
    private final List<String> streamRequestSessions = new CopyOnWriteArrayList<>();
    private final AtomicInteger streamsOpened = new AtomicInteger();
    private final AtomicInteger streamsOpenNow = new AtomicInteger();

    private final boolean deletedStreamsStayOpen;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final HttpServer server;

    private StatefulStreamableServer(boolean deletedStreamsStayOpen) throws IOException {
        this.deletedStreamsStayOpen = deletedStreamsStayOpen;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    static StatefulStreamableServer keepingDeletedStreams() throws IOException {
        return new StatefulStreamableServer(true);
    }

    static StatefulStreamableServer endingDeletedSessions() throws IOException {
        return new StatefulStreamableServer(false);
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
    }

    Set<String> issued() {
        return Set.copyOf(issued);
    }

    Set<String> open() {
        return Set.copyOf(open);
    }

    Set<String> deleted() {
        return Set.copyOf(deleted);
    }

    List<String> streamRequestSessions() {
        return List.copyOf(streamRequestSessions);
    }

    int streamsOpened() {
        return streamsOpened.get();
    }

    int streamsOpenNow() {
        return streamsOpenNow.get();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String session = exchange.getRequestHeaders().getFirst(SESSION_HEADER);
            switch (exchange.getRequestMethod()) {
                case "POST" -> post(exchange, session);
                case "GET" -> stream(exchange, session);
                case "DELETE" -> delete(exchange, session);
                default -> send(exchange, 405, null, "");
            }
        }
    }

    private void post(HttpExchange exchange, String session) throws IOException {
        JsonNode message = JSON.readTree(exchange.getRequestBody());
        String method = message.path("method").asText();
        if (method.equals("initialize")) {
            initialize(exchange, message);
        } else if (session == null) {
            send(exchange, 400, null, "Missing session ID");
        } else if (!open.contains(session)) {
            send(exchange, 404, null, "Session not found");
        } else if (!message.has("id")) {
            send(exchange, 202, null, "");
        } else {
            send(exchange, 200, null, reply(message, "{}"));
        }
    }

    private void initialize(HttpExchange exchange, JsonNode message) throws IOException {
        String id = UUID.randomUUID().toString();
        issued.add(id);
        open.add(id);
        String version = message.path("params").path("protocolVersion").asText("2025-03-26");
        send(exchange, 200, id, reply(message, "{\"protocolVersion\":\"" + version
                + "\",\"capabilities\":{},\"serverInfo\":{\"name\":\"stateful\",\"version\":\"1\"}}"));
    }

    private void delete(HttpExchange exchange, String session) throws IOException {
        if (session == null || !open.remove(session)) {
            send(exchange, 404, null, "Session not found");
            return;
        }
        deleted.add(session);
        send(exchange, 200, null, "");
    }

    private void stream(HttpExchange exchange, String session) throws IOException {
        if (session != null) {
            streamRequestSessions.add(session);
        }
        if (!streamable(session)) {
            send(exchange, 404, null, "Session not found");
            return;
        }
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        streamsOpened.incrementAndGet();
        streamsOpenNow.incrementAndGet();
        try {
            exchange.sendResponseHeaders(200, 0);
            holdUntilClientCloses(exchange.getResponseBody());
        } finally {
            streamsOpenNow.decrementAndGet();
        }
    }

    private boolean streamable(String session) {
        return session != null && (open.contains(session) || deletedStreamsStayOpen && deleted.contains(session));
    }

    private static void holdUntilClientCloses(OutputStream body) {
        Instant deadline = Instant.now().plus(MAX_STREAM);
        try {
            while (Instant.now().isBefore(deadline)) {
                body.write(": keepalive\n\n".getBytes(StandardCharsets.UTF_8));
                body.flush();
                Thread.sleep(KEEPALIVE.toMillis());
            }
        } catch (IOException clientClosed) {
            return;
        } catch (InterruptedException serverStopping) {
            Thread.currentThread().interrupt();
        }
    }

    private static String reply(JsonNode message, String result) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + message.path("id") + ",\"result\":" + result + "}";
    }

    private static void send(HttpExchange exchange, int status, String sessionId, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (body.startsWith("{")) {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
        }
        if (sessionId != null) {
            exchange.getResponseHeaders().add(SESSION_HEADER, sessionId);
        }
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
    }
}
