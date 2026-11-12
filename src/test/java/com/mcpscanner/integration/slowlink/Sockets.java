package com.mcpscanner.integration.slowlink;

import java.io.IOException;
import java.net.Socket;

final class Sockets {

    private Sockets() {
    }

    static void closeQuietly(Socket... sockets) {
        for (Socket socket : sockets) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
