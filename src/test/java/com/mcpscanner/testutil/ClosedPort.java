package com.mcpscanner.testutil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

public final class ClosedPort {

    private ClosedPort() {
    }

    public static int onLoopback() throws IOException {
        try (ServerSocket closed = new ServerSocket()) {
            closed.bind(new InetSocketAddress("127.0.0.1", 0));
            return closed.getLocalPort();
        }
    }
}
