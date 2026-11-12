package com.mcpscanner.auth.oauth;

import java.net.URI;

record LoopbackCallback(int port) {

    static final String PATH = "/callback";
    private static final String LOOPBACK_IP_LITERAL = "127.0.0.1";

    URI uri() {
        return URI.create("http://" + LOOPBACK_IP_LITERAL + ":" + port + PATH);
    }
}
