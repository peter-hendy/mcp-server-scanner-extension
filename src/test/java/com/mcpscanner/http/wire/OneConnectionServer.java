package com.mcpscanner.http.wire;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class OneConnectionServer implements AutoCloseable {

    static final String REPLY = "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok";
    private static final long AWAIT_SECONDS = 10;

    public record Received(String head, Optional<String> serverName) {
    }

    private final ServerSocket listener;
    private final CompletableFuture<Received> received = new CompletableFuture<>();
    private final CompletableFuture<Void> released = new CompletableFuture<>();

    private OneConnectionServer(ServerSocket listener, Optional<String> reply) {
        this.listener = listener;
        CompletableFuture.runAsync(() -> serve(reply));
    }

    static OneConnectionServer replying() throws IOException {
        return replyingWith(REPLY);
    }

    public static OneConnectionServer replyingWith(String reply) throws IOException {
        return new OneConnectionServer(plainListener(), Optional.of(reply));
    }

    public static OneConnectionServer silent() throws IOException {
        return new OneConnectionServer(plainListener(), Optional.empty());
    }

    static OneConnectionServer replyingOverTls(SSLContext server) throws IOException {
        return new OneConnectionServer(
                server.getServerSocketFactory().createServerSocket(0, 1, InetAddress.getLoopbackAddress()),
                Optional.of(REPLY));
    }

    public int port() {
        return listener.getLocalPort();
    }

    public Received received() throws Exception {
        return received.get(AWAIT_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void close() throws IOException {
        released.complete(null);
        listener.close();
    }

    private static ServerSocket plainListener() throws IOException {
        return new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    }

    private void serve(Optional<String> reply) {
        try (Socket connection = listener.accept()) {
            Optional<String> serverName = serverNameOf(connection);
            received.complete(new Received(head(connection.getInputStream()), serverName));
            if (reply.isPresent()) {
                connection.getOutputStream().write(reply.get().getBytes(StandardCharsets.ISO_8859_1));
                connection.getOutputStream().flush();
            }
            released.get(AWAIT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception failure) {
            received.completeExceptionally(failure);
        }
    }

    private static Optional<String> serverNameOf(Socket connection) throws IOException {
        if (!(connection instanceof SSLSocket tls)) {
            return Optional.empty();
        }
        tls.startHandshake();
        List<SNIServerName> names = ((ExtendedSSLSession) tls.getSession()).getRequestedServerNames();
        return names.stream().filter(SNIHostName.class::isInstance)
                .map(name -> ((SNIHostName) name).getAsciiName()).findFirst();
    }

    private static String head(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        while (!head.toString(StandardCharsets.ISO_8859_1).endsWith("\r\n\r\n")) {
            int next = in.read();
            if (next == -1) {
                break;
            }
            head.write(next);
        }
        return head.toString(StandardCharsets.ISO_8859_1);
    }
}
