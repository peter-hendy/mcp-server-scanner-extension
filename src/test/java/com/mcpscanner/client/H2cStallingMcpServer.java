package com.mcpscanner.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class H2cStallingMcpServer implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, String> RESULTS = Map.of(
            "initialize", "{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{\"tools\":{}},"
                    + "\"serverInfo\":{\"name\":\"h2c-stalling\",\"version\":\"1\"}}",
            "tools/list", "{\"tools\":[]}",
            "resources/list", "{\"resources\":[]}",
            "resources/templates/list", "{\"resourceTemplates\":[]}",
            "prompts/list", "{\"prompts\":[]}");

    private final ServerSocket socket;
    private final ExecutorService connections = Executors.newCachedThreadPool();
    private final CountDownLatch closed = new CountDownLatch(1);

    H2cStallingMcpServer() throws IOException {
        socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        connections.submit(this::accept);
    }

    String endpoint() {
        return "http://127.0.0.1:" + socket.getLocalPort() + "/mcp";
    }

    @Override
    public void close() throws IOException {
        closed.countDown();
        socket.close();
        connections.shutdownNow();
    }

    private void accept() {
        while (!socket.isClosed()) {
            try {
                Socket connection = socket.accept();
                connections.submit(() -> serve(connection));
            } catch (IOException closed) {
                return;
            }
        }
    }

    private void serve(Socket connection) {
        try (connection) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = connection.getOutputStream();
            while (true) {
                Request request = Request.read(reader);
                if (request == null || request.upgradesToH2c()) {
                    closed.await();
                    return;
                }
                out.write(reply(request.body()).getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (IOException | InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }

    private static String reply(String body) throws IOException {
        JsonNode message = JSON.readTree(body.isEmpty() ? "{}" : body);
        if (!message.has("id")) {
            return "HTTP/1.1 202 Accepted\r\nContent-Length: 0\r\n\r\n";
        }
        String result = RESULTS.getOrDefault(message.path("method").asText(), "{}");
        String json = "{\"jsonrpc\":\"2.0\",\"id\":" + message.get("id") + ",\"result\":" + result + "}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nMcp-Session-Id: h2c-stalling\r\n"
                + "Content-Length: " + bytes.length + "\r\n\r\n" + json;
    }

    private record Request(boolean upgradesToH2c, String body) {

        static Request read(BufferedReader reader) throws IOException {
            String line = reader.readLine();
            if (line == null) {
                return null;
            }
            boolean h2c = false;
            int length = 0;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                String header = line.toLowerCase(Locale.ROOT);
                h2c |= header.startsWith("upgrade:") && header.contains("h2c");
                if (header.startsWith("content-length:")) {
                    length = Integer.parseInt(header.substring("content-length:".length()).strip());
                }
            }
            char[] body = new char[length];
            int read = 0;
            while (read < length) {
                read += reader.read(body, read, length - read);
            }
            return new Request(h2c, new String(body));
        }
    }
}
