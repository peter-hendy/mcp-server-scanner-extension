package com.mcpscanner.http.wire;

import java.io.IOException;
import java.net.Socket;

public record WireExchange(Socket socket, WireResponse response) implements AutoCloseable {

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
