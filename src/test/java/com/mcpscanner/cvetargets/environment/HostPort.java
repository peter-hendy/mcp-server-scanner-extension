package com.mcpscanner.cvetargets.environment;

public record HostPort(String service, String hostIp, int port) {

    @Override
    public String toString() {
        return hostIp + ":" + port + " (service " + service + ")";
    }
}
