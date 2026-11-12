package com.mcpscanner.client;

final class StaleConnectionException extends IllegalStateException {
    StaleConnectionException() {
        super("HTTP scanner connection has changed");
    }
}
