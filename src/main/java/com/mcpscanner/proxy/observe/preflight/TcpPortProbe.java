package com.mcpscanner.proxy.observe.preflight;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Production {@link PortProbe}: a short-timeout TCP connect that reports whether something is
 * listening, without exchanging any application data.
 */
public final class TcpPortProbe implements PortProbe {

    private static final int CONNECT_TIMEOUT_MILLIS = 1000;

    @Override
    public boolean isReachable(InetSocketAddress address) {
        try (Socket socket = new Socket()) {
            socket.connect(address, CONNECT_TIMEOUT_MILLIS);
            return true;
        } catch (IOException unreachable) {
            return false;
        }
    }
}
