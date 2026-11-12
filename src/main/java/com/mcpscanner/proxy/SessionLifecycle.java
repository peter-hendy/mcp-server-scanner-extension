package com.mcpscanner.proxy;

final class SessionLifecycle {

    private enum State { OPEN, DISCONNECTING, CLOSED }

    private volatile State state = State.OPEN;

    boolean isOpen() {
        return state == State.OPEN;
    }

    synchronized boolean beginDisconnect() {
        if (state != State.OPEN) return false;
        state = State.DISCONNECTING;
        return true;
    }

    synchronized boolean beginRecycle() {
        if (state == State.CLOSED) return false;
        state = State.DISCONNECTING;
        return true;
    }

    synchronized void endRecycle() {
        if (state == State.DISCONNECTING) state = State.OPEN;
    }

    synchronized void expire() {
        if (state == State.OPEN) state = State.DISCONNECTING;
    }

    synchronized boolean close() {
        State prior = state;
        state = State.CLOSED;
        return prior == State.OPEN;
    }
}
