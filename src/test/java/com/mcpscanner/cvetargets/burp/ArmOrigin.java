package com.mcpscanner.cvetargets.burp;

import java.net.URI;

record ArmOrigin(String value) {

    static ArmOrigin of(URI endpoint) {
        return new ArmOrigin(endpoint.getScheme() + "://" + endpoint.getHost() + ":" + port(endpoint));
    }

    private static int port(URI endpoint) {
        if (endpoint.getPort() != -1) {
            return endpoint.getPort();
        }
        return "https".equalsIgnoreCase(endpoint.getScheme()) ? 443 : 80;
    }
}
