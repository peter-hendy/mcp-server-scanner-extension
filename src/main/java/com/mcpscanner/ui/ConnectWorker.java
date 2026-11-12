package com.mcpscanner.ui;

import com.mcpscanner.client.BurpStreamingResponseException;
import com.mcpscanner.identity.credentials.CredentialNeed;
import com.mcpscanner.ui.connect.ConnectCoordinator;
import com.mcpscanner.ui.connect.ConnectRefusal;
import com.mcpscanner.ui.connect.ConnectionAttemptResult;
import com.mcpscanner.ui.state.Cancellable;
import com.mcpscanner.ui.state.ConnectAttempt;
import com.mcpscanner.ui.state.UiAction;

import javax.swing.SwingWorker;
import java.util.List;

final class ConnectWorker extends SwingWorker<ConnectionAttemptResult, UiAction.ConnectProgress> {

    interface Host {
        boolean isCurrent(ConnectAttempt attempt);

        void dispatch(UiAction action);

        void connected(ConnectAttempt attempt, ConnectionAttemptResult outcome);

        void connectFailed(String reason);

        void credentialsNeeded(String reason, CredentialNeed need);

        void burpRefusedStreamedReply(String reason);
    }

    private final ConnectCoordinator coordinator;
    private final ConnectAttempt attempt;
    private final Runnable terminalFailure;
    private final Host host;

    ConnectWorker(ConnectCoordinator coordinator, ConnectAttempt attempt, Runnable terminalFailure, Host host) {
        this.coordinator = coordinator;
        this.attempt = attempt;
        this.terminalFailure = terminalFailure;
        this.host = host;
    }

    Cancellable cancellable() {
        return () -> {
            if (!isDone()) {
                cancel(true);
            }
        };
    }

    @Override
    protected ConnectionAttemptResult doInBackground() {
        return coordinator.connect(attempt,
                (phase, message) -> publish(new UiAction.ConnectProgress(phase, message)), terminalFailure);
    }

    @Override
    protected void process(List<UiAction.ConnectProgress> chunks) {
        if (host.isCurrent(attempt)) {
            host.dispatch(chunks.get(chunks.size() - 1));
        }
    }

    @Override
    protected void done() {
        if (isCancelled() || !host.isCurrent(attempt)) {
            return;
        }
        try {
            host.connected(attempt, get());
        } catch (Exception ex) {
            reportFailure(ex);
        }
    }

    private void reportFailure(Exception failure) {
        if (BurpStreamingResponseException.caused(failure)) {
            host.burpRefusedStreamedReply(Failures.reason(failure));
        } else {
            String reason = Failures.reason(failure);
            ConnectRefusal.need(failure).ifPresentOrElse(need -> host.credentialsNeeded(reason, need),
                    () -> host.connectFailed(reason));
        }
    }
}
