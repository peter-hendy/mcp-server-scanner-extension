package com.mcpscanner.access;

public final class AccessRunStopped extends RuntimeException {

    public AccessRunStopped(String reason) {
        super(reason);
    }
}
