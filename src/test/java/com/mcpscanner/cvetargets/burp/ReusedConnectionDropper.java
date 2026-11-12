package com.mcpscanner.cvetargets.burp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

final class ReusedConnectionDropper implements AutoCloseable {

    private static final Set<String> HOP_HEADERS = Set.of("host", "connection", "content-length", "keep-alive",
            "transfer-encoding", "accept-encoding", "expect", "upgrade");
    private static final byte[] HEAD_END = {'\r', '\n', '\r', '\n'};

    private final ServerSocket listener;
    private final URI upstream;
    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final ExecutorService connections = Executors.newCachedThreadPool();
    private final AtomicInteger relayed = new AtomicInteger();
    private final AtomicInteger dropped = new AtomicInteger();

    private ReusedConnectionDropper(ServerSocket listener, URI upstream) {
        this.listener = listener;
        this.upstream = upstream;
    }

    static ReusedConnectionDropper inFrontOf(String upstreamEndpoint) {
        try {
            ReusedConnectionDropper dropper = new ReusedConnectionDropper(
                    new ServerSocket(0, 128, InetAddress.getLoopbackAddress()), URI.create(upstreamEndpoint));
            dropper.connections.submit(dropper::accept);
            return dropper;
        } catch (IOException bindFailure) {
            throw new UncheckedIOException(bindFailure);
        }
    }

    String endpoint() {
        return "http://127.0.0.1:" + listener.getLocalPort() + upstream.getRawPath();
    }

    int relayed() {
        return relayed.get();
    }

    int dropped() {
        return dropped.get();
    }

    private void accept() {
        while (!listener.isClosed()) {
            try {
                Socket connection = listener.accept();
                connections.submit(() -> serve(connection));
            } catch (IOException closed) {
                return;
            }
        }
    }

    private void serve(Socket connection) {
        try (connection) {
            InputStream in = connection.getInputStream();
            ParsedRequest first = ParsedRequest.read(in);
            if (first == null) return;
            relay(first, connection.getOutputStream());
            if (ParsedRequest.read(in) != null) {
                dropped.incrementAndGet();
            }
        } catch (IOException | InterruptedException clientGone) {
            return;
        }
    }

    private void relay(ParsedRequest request, OutputStream out) throws IOException, InterruptedException {
        HttpRequest.Builder upstreamRequest = HttpRequest.newBuilder(upstream.resolve(request.target()))
                .method(request.method(), HttpRequest.BodyPublishers.ofByteArray(request.body()));
        request.headers().forEach((name, value) -> {
            if (!HOP_HEADERS.contains(name.toLowerCase(Locale.ROOT))) upstreamRequest.header(name, value);
        });
        HttpResponse<byte[]> reply = client.send(upstreamRequest.build(), HttpResponse.BodyHandlers.ofByteArray());
        StringBuilder head = new StringBuilder("HTTP/1.1 ").append(reply.statusCode()).append(" OK\r\n");
        reply.headers().map().forEach((name, values) -> {
            if (!HOP_HEADERS.contains(name.toLowerCase(Locale.ROOT)) && !name.startsWith(":")) {
                values.forEach(value -> head.append(name).append(": ").append(value).append("\r\n"));
            }
        });
        head.append("Content-Length: ").append(reply.body().length).append("\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(reply.body());
        out.flush();
        relayed.incrementAndGet();
    }

    @Override
    public void close() {
        try {
            listener.close();
        } catch (IOException ignored) {
            // closing anyway
        }
        connections.shutdownNow();
    }

    private record ParsedRequest(String method, String target, Map<String, String> headers, byte[] body) {

        static ParsedRequest read(InputStream in) throws IOException {
            byte[] headBytes = readHead(in);
            if (headBytes == null) return null;
            String[] lines = new String(headBytes, StandardCharsets.ISO_8859_1).split("\r\n");
            String[] requestLine = lines[0].split(" ");
            Map<String, String> headers = new LinkedHashMap<>();
            for (int i = 1; i < lines.length; i++) {
                int colon = lines[i].indexOf(':');
                if (colon > 0) headers.put(lines[i].substring(0, colon).trim(), lines[i].substring(colon + 1).trim());
            }
            int length = headers.entrySet().stream()
                    .filter(header -> header.getKey().equalsIgnoreCase("Content-Length"))
                    .mapToInt(header -> Integer.parseInt(header.getValue())).findFirst().orElse(0);
            return new ParsedRequest(requestLine[0], requestLine[1], headers, in.readNBytes(length));
        }

        private static byte[] readHead(InputStream in) throws IOException {
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            int matched = 0;
            int next;
            while ((next = in.read()) != -1) {
                head.write(next);
                matched = next == HEAD_END[matched] ? matched + 1 : (next == HEAD_END[0] ? 1 : 0);
                if (matched == HEAD_END.length) return head.toByteArray();
            }
            return null;
        }
    }
}
