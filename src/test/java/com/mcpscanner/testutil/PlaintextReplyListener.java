package com.mcpscanner.testutil;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class PlaintextReplyListener implements AutoCloseable {

    private static final byte[] PLAINTEXT_REPLY =
            "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII);

    private final ServerSocket socket;

    private PlaintextReplyListener(ServerSocket socket) {
        this.socket = socket;
        Thread acceptor = new Thread(this::replyToEveryConnection, "plaintext-reply-listener");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    public static PlaintextReplyListener start() throws IOException {
        return new PlaintextReplyListener(new ServerSocket(0, 50, InetAddress.getLoopbackAddress()));
    }

    public String httpsEndpoint() {
        return "https://127.0.0.1:" + socket.getLocalPort() + "/mcp";
    }

    private void replyToEveryConnection() {
        while (!socket.isClosed()) {
            try (Socket connection = socket.accept(); OutputStream out = connection.getOutputStream()) {
                out.write(PLAINTEXT_REPLY);
            } catch (IOException closedOrReset) {
                Thread.onSpinWait();
            }
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
