package com.mcpscanner.ui.state;

import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.identity.ConnectedIdentity;

public sealed interface UiConnectionState
        permits UiConnectionState.Disconnected,
                UiConnectionState.Connecting,
                UiConnectionState.Connected,
                UiConnectionState.Failed {

    ConnectionStatus status();

    static UiConnectionState disconnected() {
        return Disconnected.INSTANCE;
    }

    static UiConnectionState cancelled() {
        return new Disconnected(ConnectionStatus.cancelled());
    }

    static UiConnectionState connectionLost() {
        return new Disconnected(ConnectionStatus.connectionLost());
    }

    static UiConnectionState connecting(ConnectAttempt attempt, Cancellable cancellable,
                                        ConnectPhase phase, String message) {
        return new Connecting(attempt, cancellable, phase, message);
    }

    static UiConnectionState connected(ConnectResult result, String host, ConnectionStatus status,
                                       ConnectedIdentity connectedAs) {
        return new Connected(result, host, status, connectedAs);
    }

    static UiConnectionState failed(ConnectPhase phase, String reason) {
        return new Failed(phase, reason, null);
    }

    static UiConnectionState failed(ConnectPhase phase, String reason, String nextStep) {
        return new Failed(phase, reason, nextStep);
    }

    record Disconnected(ConnectionStatus status) implements UiConnectionState {
        private static final Disconnected INSTANCE = new Disconnected(ConnectionStatus.disconnected());
    }

    record Connecting(ConnectAttempt attempt, Cancellable cancellable, ConnectPhase phase, String message)
            implements UiConnectionState {

        public Connecting withCancellable(Cancellable replacement) {
            return new Connecting(attempt, replacement, phase, message);
        }

        public Connecting withProgress(ConnectPhase newPhase, String newMessage) {
            return new Connecting(attempt, cancellable, newPhase, newMessage);
        }

        @Override
        public ConnectionStatus status() {
            return ConnectionStatus.connecting(message);
        }
    }

    record Connected(ConnectResult result, String host, ConnectionStatus status, ConnectedIdentity connectedAs)
            implements UiConnectionState {
    }

    record Failed(ConnectPhase phase, String reason, String nextStep) implements UiConnectionState {
        @Override
        public ConnectionStatus status() {
            return ConnectionStatus.failed(phase, reason).withNextStep(nextStep);
        }
    }
}
