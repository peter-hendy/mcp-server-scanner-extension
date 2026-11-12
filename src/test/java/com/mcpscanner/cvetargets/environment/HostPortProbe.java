package com.mcpscanner.cvetargets.environment;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

@FunctionalInterface
public interface HostPortProbe {

    HostPortProbe BINDING = (hostIp, port) -> {
        try (ServerSocket ignored = new ServerSocket(port, 1, InetAddress.getByName(hostIp))) {
            return false;
        } catch (IOException bound) {
            return true;
        }
    };

    boolean inUse(String hostIp, int port);
}
