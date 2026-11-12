package com.mcpscanner.http.wire;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class DrippingTlsRecordServer implements AutoCloseable {

    private static final byte[] HANDSHAKE_RECORD_OF_16_KIB = {0x16, 0x03, 0x03, 0x40, 0x00};
    private static final long DRIP_MILLIS = 50;

    private final ServerSocket listener;

    private DrippingTlsRecordServer(ServerSocket listener) {
        this.listener = listener;
        CompletableFuture.runAsync(this::serve);
    }

    static DrippingTlsRecordServer start() throws IOException {
        return new DrippingTlsRecordServer(new ServerSocket(0, 1, InetAddress.getLoopbackAddress()));
    }

    int port() {
        return listener.getLocalPort();
    }

    @Override
    public void close() throws IOException {
        listener.close();
    }

    private void serve() {
        try (Socket connection = listener.accept()) {
            OutputStream out = connection.getOutputStream();
            out.write(HANDSHAKE_RECORD_OF_16_KIB);
            while (!listener.isClosed()) {
                out.write(0);
                out.flush();
                TimeUnit.MILLISECONDS.sleep(DRIP_MILLIS);
            }
        } catch (IOException | InterruptedException ignored) {
        }
    }
}
