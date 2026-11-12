package com.mcpscanner.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class RecordedServerReplay implements AutoCloseable {
    private final RecordedServer recording;
    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    RecordedServerReplay(RecordedServer recording) throws IOException {
        this.recording = recording;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext(recording.path(), this::handle);
        server.start();
    }

    String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + recording.path();
    }

    private void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            respond(exchange, 405, new byte[0]);
            return;
        }
        JsonNode request = McpObjectMapper.INSTANCE.readTree(exchange.getRequestBody());
        RecordedExchange reply = recording.exchangeFor(request.path("method").asText());
        reply.headers().forEach(exchange.getResponseHeaders()::set);
        String body = RecordedReplyBody.answering(reply, request.path("id"));
        respond(exchange, reply.status(), body.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
