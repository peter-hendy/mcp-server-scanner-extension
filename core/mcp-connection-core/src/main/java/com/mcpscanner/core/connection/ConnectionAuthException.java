package com.mcpscanner.core.connection;

public final class ConnectionAuthException extends RuntimeException {
    private final RecoveryResult result;
    public ConnectionAuthException(RecoveryResult result) {
        super("Connection authentication requires attention: " + result.name());
        this.result = result;
    }
    public RecoveryResult result() { return result; }
}
