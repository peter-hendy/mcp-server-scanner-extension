package com.mcpscanner.testutil;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class LegacySseMcpServer implements AutoCloseable {

    public static final String TOKEN = "Bearer valid-token";
    public static final String SECRET = "legacy-sse-secret-record";

    public enum Guard { OPEN, STREAM, MESSAGE }

    public record Call(String path, String rpcMethod, boolean authorized, int status) {
    }

    private final HttpServer server;
    private final Guard guard;
    private final Map<String, OutputStream> streams = new ConcurrentHashMap<>();
    private final AtomicInteger sessions = new AtomicInteger();
    private final List<Call> calls = Collections.synchronizedList(new ArrayList<>());

    private LegacySseMcpServer(HttpServer server, Guard guard) {
        this.server = server;
        this.guard = guard;
    }

    public static LegacySseMcpServer start(Guard guard) throws IOException {
        LegacySseMcpServer fixture = new LegacySseMcpServer(
                HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0), guard);
        fixture.server.createContext("/sse", fixture::openStream);
        fixture.server.createContext("/messages", fixture::receiveMessage);
        fixture.server.setExecutor(Executors.newCachedThreadPool());
        fixture.server.start();
        return fixture;
    }

    public String sseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/sse";
    }

    public List<Call> calls() {
        synchronized (calls) {
            return List.copyOf(calls);
        }
    }

    @Override
    public void close() {
        streams.values().forEach(LegacySseMcpServer::closeQuietly);
        server.stop(0);
    }

    private void openStream(HttpExchange exchange) throws IOException {
        boolean authorized = authorized(exchange);
        if (guard == Guard.STREAM && !authorized) {
            calls.add(new Call("/sse", null, false, refuse(exchange)));
            return;
        }
        String sessionId = "s" + sessions.incrementAndGet();
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        OutputStream stream = exchange.getResponseBody();
        streams.put(sessionId, stream);
        calls.add(new Call("/sse", null, authorized, 200));
        emit(stream, "endpoint", "/messages?sessionId=" + sessionId);
    }

    private void receiveMessage(HttpExchange exchange) throws IOException {
        boolean authorized = authorized(exchange);
        JsonNode message = McpObjectMapper.INSTANCE.readTree(exchange.getRequestBody().readAllBytes());
        String rpcMethod = message.path("method").asText(null);
        OutputStream stream = streams.get(sessionIdOf(exchange));
        if (guard == Guard.MESSAGE && !authorized) {
            calls.add(new Call("/messages", rpcMethod, false, refuse(exchange)));
            return;
        }
        if (stream == null) {
            calls.add(new Call("/messages", rpcMethod, authorized, respond(exchange, 404)));
            return;
        }
        calls.add(new Call("/messages", rpcMethod, authorized, respond(exchange, 202)));
        if (message.has("id")) {
            emit(stream, "message", McpObjectMapper.INSTANCE.writeValueAsString(Map.of(
                    "jsonrpc", "2.0", "id", message.get("id"), "result", resultFor(rpcMethod))));
        }
    }

    private static Object resultFor(String rpcMethod) {
        return switch (rpcMethod) {
            case "initialize" -> Map.of("protocolVersion", "2024-11-05", "capabilities", Map.of("tools", Map.of()),
                    "serverInfo", Map.of("name", "legacy-sse-fixture", "version", "1"));
            case "tools/list" -> Map.of("tools", List.of(Map.of("name", "read_record",
                    "inputSchema", Map.of("type", "object"))));
            case "tools/call" -> Map.of("content", List.of(Map.of("type", "text", "text", SECRET)));
            default -> Map.of();
        };
    }

    private static boolean authorized(HttpExchange exchange) {
        return TOKEN.equals(exchange.getRequestHeaders().getFirst("Authorization"));
    }

    private static String sessionIdOf(HttpExchange exchange) {
        String query = exchange.getRequestURI().getQuery();
        return query == null ? "" : query.replaceFirst("^sessionId=", "");
    }

    private static int refuse(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
        return respond(exchange, 401);
    }

    private static int respond(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
        return status;
    }

    private static void emit(OutputStream stream, String event, String data) throws IOException {
        synchronized (stream) {
            stream.write(("event: " + event + "\ndata: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
            stream.flush();
        }
    }

    private static void closeQuietly(OutputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
        }
    }
}
