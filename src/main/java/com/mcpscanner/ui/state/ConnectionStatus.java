package com.mcpscanner.ui.state;

import com.mcpscanner.client.TransportType;
import com.mcpscanner.engine.OutOfBandWatchText;
import com.mcpscanner.scan.ScanOutcome;
import com.mcpscanner.core.oauth.RegistrationMode;

import java.util.ArrayList;
import java.util.List;

public record ConnectionStatus(ConnectionState state, String message, String primaryButtonLabel) {

    private static final String SEPARATOR = " · ";
    private static final String LABEL_CONNECT = "Connect";
    private static final String LABEL_CANCEL = "Cancel";
    private static final String LABEL_DISCONNECT = "Disconnect";

    public static ConnectionStatus disconnected() {
        return new ConnectionStatus(ConnectionState.DISCONNECTED, "Disconnected", LABEL_CONNECT);
    }

    public static ConnectionStatus cancelled() {
        return new ConnectionStatus(ConnectionState.DISCONNECTED, "Connection cancelled", LABEL_CONNECT);
    }

    public static ConnectionStatus connectionLost() {
        return new ConnectionStatus(ConnectionState.DISCONNECTED,
                "Connection lost · Connect again to continue", LABEL_CONNECT);
    }

    public static ConnectionStatus credentialsCleared(String notice) {
        return new ConnectionStatus(ConnectionState.DISCONNECTED, notice, LABEL_CONNECT);
    }

    public static ConnectionStatus connecting(String progressDetail) {
        return new ConnectionStatus(ConnectionState.CONNECTING, progressDetail, LABEL_CANCEL);
    }

    public static ConnectionStatus connected(String subject, String host, Long expiresInMinutes, int toolCount) {
        return new ConnectionStatus(
                ConnectionState.CONNECTED,
                buildConnectedMessage(subject, host, expiresInMinutes, toolCount),
                LABEL_DISCONNECT);
    }

    public ConnectionStatus withNegotiatedProtocol(TransportType transport, String protocolVersion) {
        StringBuilder detail = new StringBuilder(message);
        if (transport != null) {
            detail.append(SEPARATOR).append(transport);
        }
        if (protocolVersion != null && !protocolVersion.isBlank()) {
            detail.append(SEPARATOR).append("MCP ").append(protocolVersion);
        }
        return new ConnectionStatus(state, detail.toString(), primaryButtonLabel);
    }

    public ConnectionStatus withNextStep(String nextStep) {
        if (nextStep == null || nextStep.isBlank()) return this;
        return new ConnectionStatus(state, message + SEPARATOR + nextStep, primaryButtonLabel);
    }

    public ConnectionStatus withAuthMethod(String authMethod) {
        if (authMethod == null || authMethod.isBlank()) return this;
        return new ConnectionStatus(state, message + SEPARATOR + "Auth: " + authMethod,
                primaryButtonLabel);
    }

    public ConnectionStatus withRegistrationMode(RegistrationMode mode) {
        if (mode == null) return this;
        return new ConnectionStatus(state, message + SEPARATOR + "OAuth client: " + registrationLabel(mode),
                primaryButtonLabel);
    }

    public static String registrationLabel(RegistrationMode mode) {
        return switch (mode) {
            case PRE_REGISTERED -> "Pre-registered client";
            case CIMD -> "CIMD";
            case DCR -> "DCR";
            case AUTO -> "Auto";
        };
    }

    public static ConnectionStatus failed(ConnectPhase phase, String reason) {
        return new ConnectionStatus(
                ConnectionState.FAILED, phase.displayName() + " failed: " + reason, LABEL_CONNECT);
    }

    public static ConnectionStatus scanStarted(String scannedSummary, String resultsLocation) {
        String message = "Scan started: " + scannedSummary + SEPARATOR + "Results in " + resultsLocation;
        return new ConnectionStatus(ConnectionState.CONNECTED, message, LABEL_DISCONNECT);
    }

    public static ConnectionStatus scanEnded(ScanOutcome outcome, String resultsLocation) {
        String ending = outcome.cancelled() ? "Scan cancelled" : "Scan finished";
        String issues = outcome.issues() + (outcome.issues() == 1 ? " issue" : " issues");
        String message = ending + SEPARATOR + issues + SEPARATOR + "Results in " + resultsLocation
                + watchingSuffix(outcome.watchedPayloads());
        return new ConnectionStatus(ConnectionState.CONNECTED, message, LABEL_DISCONNECT);
    }

    private static String watchingSuffix(int watchedPayloads) {
        return watchedPayloads == 0 ? "" : SEPARATOR + OutOfBandWatchText.of(watchedPayloads);
    }

    public static ConnectionStatus scanWaitingForPausedTasks(String scannedSummary) {
        String message = "Scan queued: " + scannedSummary + SEPARATOR
                + "Burp task execution is paused, so the audit will not run until you resume it";
        return new ConnectionStatus(ConnectionState.CONNECTED, message, LABEL_DISCONNECT);
    }

    public static ConnectionStatus scanCancelled() {
        return new ConnectionStatus(ConnectionState.CONNECTED, "Scan cancelled; nothing was sent", LABEL_DISCONNECT);
    }

    public static ConnectionStatus tokenRefreshed() {
        return new ConnectionStatus(ConnectionState.CONNECTED, "Access token refreshed", LABEL_DISCONNECT);
    }

    public static ConnectionStatus tokenRefreshFailed(String reason) {
        return new ConnectionStatus(ConnectionState.CONNECTED, "Token refresh failed: " + reason, LABEL_DISCONNECT);
    }

    public static ConnectionStatus scanFailed(String reason) {
        return new ConnectionStatus(ConnectionState.CONNECTED, "Scan failed: " + reason, LABEL_DISCONNECT);
    }

    private static String buildConnectedMessage(String subject, String host, Long expiresInMinutes, int toolCount) {
        List<String> segments = new ArrayList<>();
        segments.add(subject == null
                ? "Connected to " + host
                : "Connected as " + subject + " to " + host);
        if (expiresInMinutes != null) {
            segments.add("expires in " + expiresInMinutes + " min");
        }
        segments.add(toolCount + " tools");
        return String.join(SEPARATOR, segments);
    }
}
