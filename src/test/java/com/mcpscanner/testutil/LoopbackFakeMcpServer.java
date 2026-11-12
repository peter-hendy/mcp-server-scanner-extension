package com.mcpscanner.testutil;

import com.mcpscanner.client.ProtocolHttpRequest;
import com.mcpscanner.client.ProtocolHttpResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LoopbackFakeMcpServer implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<String> methods = new CopyOnWriteArrayList<>();

    private LoopbackFakeMcpServer(HttpServer server) {
        this.server = server;
        server.setExecutor(executor);
        server.createContext("/mcp", this::handle);
        server.start();
    }

    public static LoopbackFakeMcpServer start() throws IOException {
        return new LoopbackFakeMcpServer(HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0));
    }

    public String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
    }

    public List<String> methods() {
        return methods;
    }

    private void handle(HttpExchange exchange) throws IOException {
        ProtocolHttpRequest request = toProtocolRequest(exchange);
        methods.add(String.valueOf(FakeMcpServer.method(request)));
        try (ProtocolHttpResponse response = FakeMcpServer.respond(request)) {
            reply(exchange, response);
        }
    }

    private ProtocolHttpRequest toProtocolRequest(HttpExchange exchange) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, values.get(0)));
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return new ProtocolHttpRequest(URI.create(endpoint()), exchange.getRequestMethod(), headers, body);
    }

    private static void reply(HttpExchange exchange, ProtocolHttpResponse response) throws IOException {
        response.headers().forEach((name, values) -> exchange.getResponseHeaders().put(name, values));
        byte[] body = response.body().readAllBytes();
        exchange.sendResponseHeaders(response.status(), body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
